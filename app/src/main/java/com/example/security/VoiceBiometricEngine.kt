package com.example.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceSampleFeatures(
    val embedding: FloatArray,
    val voicedFrameCount: Int,
    val totalFrameCount: Int,
    val durationMs: Long,
    val meanPitchHz: Float,
    val pitchConfidence: Float,
    val rmsEnergy: Float
) {
    val voicedRatio: Float
        get() = if (totalFrameCount > 0) voicedFrameCount.toFloat() / totalFrameCount else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceSampleFeatures
        return embedding.contentEquals(other.embedding) &&
            voicedFrameCount == other.voicedFrameCount &&
            totalFrameCount == other.totalFrameCount &&
            durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + voicedFrameCount
        result = 31 * result + totalFrameCount
        result = 31 * result + durationMs.hashCode()
        return result
    }
}

data class VoiceprintTemplate(
    val version: Int = 1,
    val embedding: FloatArray,
    val meanPitchHz: Float,
    val pitchConfidence: Float,
    val intraSpeakerConsistency: Float,
    val sampleCount: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceprintTemplate
        return version == other.version &&
            embedding.contentEquals(other.embedding) &&
            meanPitchHz == other.meanPitchHz &&
            sampleCount == other.sampleCount
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + meanPitchHz.hashCode()
        result = 31 * result + sampleCount
        return result
    }
}

data class VoiceVerificationResult(
    val isVerified: Boolean,
    val similarityScore: Float,
    val confidencePercent: Int,
    val qualityMessage: String,
    val pitchHz: Float = 0f,
    val voicedFrames: Int = 0
)

data class VoiceCaptureState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val rmsLevel: Float = 0f,
    val spectrumBands: List<Float> = List(16) { 0f },
    val errorMessage: String? = null
)

/**
 * On-device acoustic Voice Biometric Recognition engine.
 *
 * Performs genuine digital signal processing on 16 kHz 16-bit PCM speech:
 * 1. DC offset removal and pre-emphasis filtering (alpha = 0.97)
 * 2. Adaptive noise-floor Voice Activity Detection (VAD)
 * 3. Hamming-windowed 512-point Cooley-Tukey Radix-2 FFT
 * 4. 26-band triangular Mel-frequency filterbank + Log-Mel + DCT-II (13 MFCCs + 13 deltas)
 * 5. Autocorrelation F0 vocal pitch & spectral centroid estimation
 * 6. L2-normalized 42-dimensional acoustic speaker embedding & cosine verification
 */
object VoiceBiometricEngine {

    const val SAMPLE_RATE = 16_000
    const val FRAME_SIZE = 400 // 25 ms @ 16 kHz
    const val HOP_SIZE = 160   // 10 ms @ 16 kHz
    const val FFT_SIZE = 512
    const val NUM_MEL_FILTERS = 26
    const val NUM_MFCC = 13
    const val EMBEDDING_DIM = NUM_MFCC * 3 + 3 // 13 mean MFCC + 13 std MFCC + 13 mean delta-MFCC + 3 prosody

    const val MIN_DURATION_MS = 1_100L
    const val MIN_VOICED_FRAMES = 22
    const val ENROLLMENT_SAMPLES_REQUIRED = 3

    const val ENROLLMENT_CONSISTENCY_THRESHOLD = 0.78f
    const val VERIFICATION_THRESHOLD = 0.85f

    private const val MIN_VOICE_RMS = 0.008f
    private const val PRE_EMPHASIS_ALPHA = 0.97f
    private const val MIN_PITCH_HZ = 75f
    private const val MAX_PITCH_HZ = 400f

    private val hammingWindow: FloatArray by lazy {
        FloatArray(FRAME_SIZE) { n ->
            (0.54 - 0.46 * cos(2.0 * PI * n / (FRAME_SIZE - 1))).toFloat()
        }
    }

    private val melFilterbank: Array<FloatArray> by lazy {
        buildMelFilterbank(SAMPLE_RATE, FFT_SIZE, NUM_MEL_FILTERS, 100.0, 7600.0)
    }

    private val dctMatrix: Array<FloatArray> by lazy {
        // Compute DCT-II rows for coefficients c = 1..NUM_MFCC (excluding c=0 overall energy)
        Array(NUM_MFCC) { rowIdx ->
            val coeffIdx = rowIdx + 1
            FloatArray(NUM_MEL_FILTERS) { m ->
                (sqrt(2.0 / NUM_MEL_FILTERS) * cos(PI * coeffIdx * (m + 0.5) / NUM_MEL_FILTERS)).toFloat()
            }
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Extracts a 42-D L2-normalized acoustic speaker embedding from raw 16-bit mono PCM samples.
     */
    fun extractSpeakerProfile(
        pcmSamples: ShortArray,
        sampleRate: Int = SAMPLE_RATE
    ): Result<VoiceSampleFeatures> {
        val durationMs = (pcmSamples.size * 1000L) / max(sampleRate, 1)
        if (durationMs < MIN_DURATION_MS || pcmSamples.size < FRAME_SIZE * 10) {
            return Result.failure(
                IllegalArgumentException("Recording too short. Speak clearly for at least 1.5 seconds.")
            )
        }

        // Convert to normalized float & remove DC offset
        var meanSample = 0.0
        for (s in pcmSamples) {
            meanSample += s / 32768.0
        }
        meanSample /= pcmSamples.size

        val normalized = FloatArray(pcmSamples.size)
        var sumSquares = 0.0
        for (i in pcmSamples.indices) {
            val v = ((pcmSamples[i] / 32768.0) - meanSample).toFloat()
            normalized[i] = v
            sumSquares += v * v
        }
        val globalRms = sqrt(sumSquares / pcmSamples.size).toFloat()
        if (globalRms < MIN_VOICE_RMS) {
            return Result.failure(
                IllegalArgumentException("Voice signal too quiet or microphone muted. Speak louder and closer.")
            )
        }

        // Pre-emphasis filter for spectral whitening
        val preEmphasized = FloatArray(normalized.size)
        preEmphasized[0] = normalized[0]
        for (i in 1 until normalized.size) {
            preEmphasized[i] = normalized[i] - PRE_EMPHASIS_ALPHA * normalized[i - 1]
        }

        val numFrames = ((preEmphasized.size - FRAME_SIZE) / HOP_SIZE) + 1
        if (numFrames < MIN_VOICED_FRAMES) {
            return Result.failure(IllegalArgumentException("Insufficient audio frames captured."))
        }

        val frameEnergies = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * HOP_SIZE
            var e = 0.0
            for (n in 0 until FRAME_SIZE) {
                val x = normalized[start + n]
                e += x * x
            }
            frameEnergies[f] = sqrt(e / FRAME_SIZE).toFloat()
        }

        // Adaptive noise-floor Voice Activity Detection (VAD)
        val sortedEnergies = frameEnergies.sortedArray()
        val noiseFloor = sortedEnergies[(sortedEnergies.size * 0.20).toInt().coerceIn(0, sortedEnergies.lastIndex)]
        val peakEnergy = sortedEnergies.last()
        val vadThreshold = max(noiseFloor * 2.2f, max(MIN_VOICE_RMS, peakEnergy * 0.16f))

        val activeFrameIndices = mutableListOf<Int>()
        for (f in 0 until numFrames) {
            if (frameEnergies[f] >= vadThreshold) {
                activeFrameIndices.add(f)
            }
        }

        if (activeFrameIndices.size < MIN_VOICED_FRAMES) {
            return Result.failure(
                IllegalArgumentException("Not enough active speech detected ($MIN_VOICED_FRAMES voiced frames required). Try speaking a full phrase.")
            )
        }

        val mfccFrames = ArrayList<FloatArray>(activeFrameIndices.size)
        val spectralCentroids = ArrayList<Float>(activeFrameIndices.size)
        val pitchEstimates = ArrayList<Pair<Float, Float>>() // (pitchHz, confidence)

        val fftReal = FloatArray(FFT_SIZE)
        val fftImag = FloatArray(FFT_SIZE)
        val halfFftBins = FFT_SIZE / 2 + 1
        val powerSpectrum = FloatArray(halfFftBins)
        val nyquist = sampleRate / 2f

        for (frameIdx in activeFrameIndices) {
            val start = frameIdx * HOP_SIZE

            // Apply Hamming window on pre-emphasized frame
            fftReal.fill(0f)
            fftImag.fill(0f)
            for (n in 0 until FRAME_SIZE) {
                fftReal[n] = preEmphasized[start + n] * hammingWindow[n]
            }

            fftInPlace(fftReal, fftImag)

            var weightedFreqSum = 0.0
            var magSum = 0.0
            for (k in 0 until halfFftBins) {
                val re = fftReal[k]
                val im = fftImag[k]
                val pwr = (re * re + im * im) / FFT_SIZE
                powerSpectrum[k] = pwr
                val mag = sqrt(pwr.toDouble())
                val freq = k.toDouble() * nyquist / (halfFftBins - 1)
                weightedFreqSum += freq * mag
                magSum += mag
            }
            val centroidNorm = if (magSum > 1e-9) ((weightedFreqSum / magSum) / nyquist).toFloat().coerceIn(0f, 1f) else 0f
            spectralCentroids.add(centroidNorm)

            // Mel filterbank energies -> log -> DCT-II MFCC
            val logMel = FloatArray(NUM_MEL_FILTERS)
            for (m in 0 until NUM_MEL_FILTERS) {
                val filter = melFilterbank[m]
                var energy = 1e-10
                for (k in 0 until halfFftBins) {
                    energy += powerSpectrum[k] * filter[k]
                }
                logMel[m] = ln(energy).toFloat()
            }

            val mfcc = FloatArray(NUM_MFCC)
            for (c in 0 until NUM_MFCC) {
                var sum = 0.0
                val dctRow = dctMatrix[c]
                for (m in 0 until NUM_MEL_FILTERS) {
                    sum += dctRow[m] * logMel[m]
                }
                mfcc[c] = sum.toFloat()
            }
            mfccFrames.add(mfcc)

            // Pitch estimation on un-emphasized audio frame via normalized autocorrelation
            val pitchPair = estimateFramePitch(normalized, start, FRAME_SIZE, sampleRate)
            if (pitchPair != null) {
                pitchEstimates.add(pitchPair)
            }
        }

        // Compute MFCC mean and std
        val mfccMean = FloatArray(NUM_MFCC)
        val mfccStd = FloatArray(NUM_MFCC)
        val mCount = mfccFrames.size.toFloat()

        for (frame in mfccFrames) {
            for (c in 0 until NUM_MFCC) {
                mfccMean[c] += frame[c]
            }
        }
        for (c in 0 until NUM_MFCC) {
            mfccMean[c] /= mCount
        }
        for (frame in mfccFrames) {
            for (c in 0 until NUM_MFCC) {
                val diff = frame[c] - mfccMean[c]
                mfccStd[c] += diff * diff
            }
        }
        for (c in 0 until NUM_MFCC) {
            mfccStd[c] = sqrt(mfccStd[c] / mCount)
        }

        // Compute Delta-MFCC mean (rate of vocal tract articulation)
        val deltaMean = FloatArray(NUM_MFCC)
        if (mfccFrames.size >= 3) {
            val deltaCount = (mfccFrames.size - 2).toFloat()
            for (i in 1 until mfccFrames.size - 1) {
                for (c in 0 until NUM_MFCC) {
                    val delta = (mfccFrames[i + 1][c] - mfccFrames[i - 1][c]) * 0.5f
                    deltaMean[c] += abs(delta)
                }
            }
            for (c in 0 until NUM_MFCC) {
                deltaMean[c] /= deltaCount
            }
        }

        // Vocal prosody & pitch features
        val voicedPitchHz: Float
        val pitchConf: Float
        if (pitchEstimates.isNotEmpty()) {
            val sortedPitches = pitchEstimates.map { it.first }.sorted()
            voicedPitchHz = sortedPitches[sortedPitches.size / 2]
            pitchConf = pitchEstimates.map { it.second }.average().toFloat()
        } else {
            voicedPitchHz = 0f
            pitchConf = 0f
        }

        // Reject pure non-harmonic noise if periodicity is essentially zero across entire clip
        val hasHarmonicStructure = pitchEstimates.count { it.second >= 0.35f } >= max(4, activeFrameIndices.size / 8)
        if (!hasHarmonicStructure && pitchConf < 0.25f) {
            return Result.failure(
                IllegalArgumentException("No clear vocal pitch detected. Avoid background static and speak naturally.")
            )
        }

        val meanCentroid = spectralCentroids.average().toFloat()
        val pitchNorm = if (voicedPitchHz > 0f) {
            ((voicedPitchHz - MIN_PITCH_HZ) / (MAX_PITCH_HZ - MIN_PITCH_HZ)).coerceIn(0f, 1f)
        } else 0.5f
        val voicedRatio = activeFrameIndices.size.toFloat() / numFrames

        // Assemble 42-D speaker embedding (weighted smoothly between spectral timbre and prosody)
        val embedding = FloatArray(EMBEDDING_DIM)
        for (c in 0 until NUM_MFCC) {
            embedding[c] = mfccMean[c]
            embedding[NUM_MFCC + c] = mfccStd[c] * 0.65f
            embedding[NUM_MFCC * 2 + c] = deltaMean[c] * 0.50f
        }
        embedding[NUM_MFCC * 3] = pitchNorm * 3.0f
        embedding[NUM_MFCC * 3 + 1] = voicedRatio * 2.0f
        embedding[NUM_MFCC * 3 + 2] = meanCentroid * 2.5f

        l2NormalizeInPlace(embedding)

        return Result.success(
            VoiceSampleFeatures(
                embedding = embedding,
                voicedFrameCount = activeFrameIndices.size,
                totalFrameCount = numFrames,
                durationMs = durationMs,
                meanPitchHz = voicedPitchHz,
                pitchConfidence = pitchConf,
                rmsEnergy = globalRms
            )
        )
    }

    /**
     * Combines multiple enrollment recordings (typically 3) into a single validated speaker centroid.
     */
    fun buildEnrollmentTemplate(
        samples: List<ShortArray>,
        sampleRate: Int = SAMPLE_RATE
    ): Result<VoiceprintTemplate> {
        if (samples.size < ENROLLMENT_SAMPLES_REQUIRED) {
            return Result.failure(
                IllegalArgumentException("Require at least $ENROLLMENT_SAMPLES_REQUIRED voice samples for enrollment.")
            )
        }

        val extracted = mutableListOf<VoiceSampleFeatures>()
        for ((index, pcm) in samples.withIndex()) {
            val res = extractSpeakerProfile(pcm, sampleRate)
            if (res.isFailure) {
                return Result.failure(
                    IllegalArgumentException("Sample ${index + 1} invalid: ${res.exceptionOrNull()?.message}")
                )
            }
            extracted.add(res.getOrThrow())
        }

        // Verify pairwise intra-speaker consistency so background noise or mixed voices aren't enrolled
        var simSum = 0f
        var pairCount = 0
        var minPairSim = 1f
        for (i in extracted.indices) {
            for (j in i + 1 until extracted.size) {
                val sim = cosineSimilarity(extracted[i].embedding, extracted[j].embedding)
                simSum += sim
                pairCount++
                if (sim < minPairSim) minPairSim = sim
            }
        }
        val meanConsistency = if (pairCount > 0) simSum / pairCount else 1f
        if (minPairSim < ENROLLMENT_CONSISTENCY_THRESHOLD) {
            return Result.failure(
                IllegalArgumentException(
                    "Voice samples varied too much (${(minPairSim * 100).toInt()}% match). " +
                        "Please speak the same passphrase consistently in a quiet environment."
                )
            )
        }

        // Compute normalized speaker centroid
        val centroid = FloatArray(EMBEDDING_DIM)
        for (feat in extracted) {
            for (k in centroid.indices) {
                centroid[k] += feat.embedding[k]
            }
        }
        l2NormalizeInPlace(centroid)

        val validPitches = extracted.filter { it.meanPitchHz > 0f }
        val avgPitch = if (validPitches.isNotEmpty()) validPitches.map { it.meanPitchHz }.average().toFloat() else 0f
        val avgConf = extracted.map { it.pitchConfidence }.average().toFloat()

        return Result.success(
            VoiceprintTemplate(
                version = 1,
                embedding = centroid,
                meanPitchHz = avgPitch,
                pitchConfidence = avgConf,
                intraSpeakerConsistency = meanConsistency,
                sampleCount = extracted.size,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Verifies a live audio sample against a decrypted enrolled [VoiceprintTemplate].
     */
    fun verifyAgainstTemplate(
        pcmSamples: ShortArray,
        template: VoiceprintTemplate,
        sampleRate: Int = SAMPLE_RATE
    ): VoiceVerificationResult {
        val sampleResult = extractSpeakerProfile(pcmSamples, sampleRate)
        if (sampleResult.isFailure) {
            return VoiceVerificationResult(
                isVerified = false,
                similarityScore = 0f,
                confidencePercent = 0,
                qualityMessage = sampleResult.exceptionOrNull()?.message ?: "Voice capture invalid"
            )
        }

        val live = sampleResult.getOrThrow()
        var similarity = cosineSimilarity(live.embedding, template.embedding)

        // Enforce vocal fundamental-frequency consistency when both recordings have high pitch confidence
        if (live.meanPitchHz > 0f && template.meanPitchHz > 0f &&
            live.pitchConfidence >= 0.40f && template.pitchConfidence >= 0.40f
        ) {
            val pitchRatioDiff = abs(live.meanPitchHz - template.meanPitchHz) / template.meanPitchHz
            if (pitchRatioDiff > 0.45f) {
                // Significant pitch-octave/speaker shift penalty
                similarity *= (1f - min(0.25f, (pitchRatioDiff - 0.45f) * 0.5f))
            }
        }

        val clampedSim = similarity.coerceIn(0f, 1f)
        val percent = (clampedSim * 100f).toInt().coerceIn(0, 100)
        val verified = clampedSim >= VERIFICATION_THRESHOLD

        val message = if (verified) {
            "Voiceprint verified ($percent% acoustic match)"
        } else {
            "Voiceprint mismatch ($percent% match; require ${(VERIFICATION_THRESHOLD * 100).toInt()}%+)"
        }

        return VoiceVerificationResult(
            isVerified = verified,
            similarityScore = clampedSim,
            confidencePercent = percent,
            qualityMessage = message,
            pitchHz = live.meanPitchHz,
            voicedFrames = live.voicedFrameCount
        )
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-9) (dot / denom).toFloat() else 0f
    }

    fun serializeTemplate(template: VoiceprintTemplate): String {
        val arr = JSONArray()
        for (v in template.embedding) {
            arr.put(v.toDouble())
        }
        return JSONObject().apply {
            put("version", template.version)
            put("embedding", arr)
            put("meanPitchHz", template.meanPitchHz.toDouble())
            put("pitchConfidence", template.pitchConfidence.toDouble())
            put("intraSpeakerConsistency", template.intraSpeakerConsistency.toDouble())
            put("sampleCount", template.sampleCount)
            put("createdAt", template.createdAt)
        }.toString()
    }

    fun deserializeTemplate(jsonString: String): VoiceprintTemplate {
        val json = JSONObject(jsonString)
        val arr = json.getJSONArray("embedding")
        val embedding = FloatArray(arr.length()) { idx -> arr.getDouble(idx).toFloat() }
        return VoiceprintTemplate(
            version = json.optInt("version", 1),
            embedding = embedding,
            meanPitchHz = json.optDouble("meanPitchHz", 0.0).toFloat(),
            pitchConfidence = json.optDouble("pitchConfidence", 0.0).toFloat(),
            intraSpeakerConsistency = json.optDouble("intraSpeakerConsistency", 1.0).toFloat(),
            sampleCount = json.optInt("sampleCount", ENROLLMENT_SAMPLES_REQUIRED),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun estimateFramePitch(
        signal: FloatArray,
        offset: Int,
        frameSize: Int,
        sampleRate: Int
    ): Pair<Float, Float>? {
        val minLag = (sampleRate / MAX_PITCH_HZ).toInt().coerceAtLeast(16)
        val maxLag = (sampleRate / MIN_PITCH_HZ).toInt().coerceAtMost(frameSize - 20)
        if (maxLag <= minLag) return null

        var energyZero = 0.0
        for (i in 0 until frameSize) {
            val s = signal[offset + i]
            energyZero += s * s
        }
        if (energyZero < 1e-6) return null

        var bestLag = -1
        var bestCorr = 0f

        for (lag in minLag..maxLag) {
            var cross = 0.0
            var energyDelayed = 0.0
            val limit = frameSize - lag
            for (i in 0 until limit) {
                val a = signal[offset + i]
                val b = signal[offset + i + lag]
                cross += a * b
                energyDelayed += b * b
            }
            val normDenom = sqrt(energyZero * energyDelayed)
            if (normDenom > 1e-9) {
                val r = (cross / normDenom).toFloat()
                if (r > bestCorr) {
                    bestCorr = r
                    bestLag = lag
                }
            }
        }

        return if (bestLag > 0 && bestCorr >= 0.30f) {
            val pitchHz = sampleRate.toFloat() / bestLag
            Pair(pitchHz, bestCorr)
        } else {
            null
        }
    }

    private fun l2NormalizeInPlace(vec: FloatArray) {
        var normSq = 0.0
        for (v in vec) {
            normSq += v * v
        }
        val norm = sqrt(normSq).toFloat()
        if (norm > 1e-9f) {
            for (i in vec.indices) {
                vec[i] /= norm
            }
        }
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun buildMelFilterbank(
        sampleRate: Int,
        fftSize: Int,
        numFilters: Int,
        lowFreqHz: Double,
        highFreqHz: Double
    ): Array<FloatArray> {
        val lowMel = hzToMel(lowFreqHz)
        val highMel = hzToMel(highFreqHz)
        val melPoints = DoubleArray(numFilters + 2) { i ->
            lowMel + i * (highMel - lowMel) / (numFilters + 1)
        }
        val binIndices = IntArray(numFilters + 2) { i ->
            val hz = melToHz(melPoints[i])
            ((fftSize + 1) * hz / sampleRate).toInt().coerceIn(0, fftSize / 2)
        }

        val halfBins = fftSize / 2 + 1
        return Array(numFilters) { m ->
            val filter = FloatArray(halfBins)
            val left = binIndices[m]
            val center = binIndices[m + 1]
            val right = binIndices[m + 2]

            if (center > left) {
                for (k in left until center) {
                    filter[k] = ((k - left).toDouble() / (center - left)).toFloat()
                }
            }
            if (right > center) {
                for (k in center..right) {
                    filter[k] = ((right - k).toDouble() / (right - center)).toFloat()
                }
            }
            filter
        }
    }

    /**
     * In-place Cooley-Tukey Radix-2 FFT.
     */
    fun fftInPlace(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpRe = real[i]
                val tmpIm = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tmpRe
                imag[j] = tmpIm
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1.0f
                var wIm = 0.0f
                val half = len shr 1
                for (k in 0 until half) {
                    val evenIdx = i + k
                    val oddIdx = evenIdx + half
                    val uRe = real[evenIdx]
                    val uIm = imag[evenIdx]
                    val tRe = wRe * real[oddIdx] - wIm * imag[oddIdx]
                    val tIm = wRe * imag[oddIdx] + wIm * real[oddIdx]
                    real[evenIdx] = uRe + tRe
                    imag[evenIdx] = uIm + tIm
                    real[oddIdx] = uRe - tRe
                    imag[oddIdx] = uIm - tIm
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes 16 normalized spectrum bar levels from a raw PCM chunk for real-time audio visualization.
     */
    fun computeLiveSpectrumBands(chunk: ShortArray, count: Int): List<Float> {
        if (count <= 0) return List(16) { 0f }
        val fftSize = 512
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val copyLen = min(count, fftSize)
        for (i in 0 until copyLen) {
            re[i] = chunk[i] / 32768f
        }
        fftInPlace(re, im)

        val numBands = 16
        val usefulBins = 240 // Up to ~7.5 kHz @ 16 kHz
        val bands = MutableList(numBands) { 0f }
        for (b in 0 until numBands) {
            val startBin = 2 + (b * (usefulBins - 2)) / numBands
            val endBin = (2 + ((b + 1) * (usefulBins - 2)) / numBands).coerceAtLeast(startBin + 1)
            var sumMag = 0f
            for (k in startBin until endBin) {
                sumMag += sqrt(re[k] * re[k] + im[k] * im[k])
            }
            val avgMag = sumMag / (endBin - startBin)
            // Log-compressed visual scale normalized to [0..1]
            bands[b] = (ln(1.0 + avgMag * 2.5) / 3.2).toFloat().coerceIn(0.05f, 1f)
        }
        return bands
    }
}

/**
 * Real Android [AudioRecord] PCM microphone capture session with live RMS & FFT spectrum telemetry.
 */
class VoiceAudioCaptureSession {

    private val _captureState = MutableStateFlow(VoiceCaptureState())
    val captureState: StateFlow<VoiceCaptureState> = _captureState.asStateFlow()

    @Volatile
    private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission")
    suspend fun recordUtterance(
        context: Context,
        maxDurationMs: Long = 3_200L
    ): Result<ShortArray> = withContext(Dispatchers.IO) {
        if (!VoiceBiometricEngine.hasAudioPermission(context)) {
            return@withContext Result.failure(SecurityException("Microphone permission (RECORD_AUDIO) not granted."))
        }

        stopRequested = false
        val sampleRate = VoiceBiometricEngine.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            return@withContext Result.failure(IllegalStateException("Device microphone buffer unavailable ($minBuf)."))
        }

        val bufferSize = max(minBuf * 2, sampleRate)
        var recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            val msg = "Unable to initialize Android microphone hardware."
            _captureState.value = VoiceCaptureState(errorMessage = msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val maxSamples = ((sampleRate * maxDurationMs) / 1000L).toInt()
        val captured = ShortArray(maxSamples)
        var totalSamples = 0
        val readChunk = ShortArray(512)

        _captureState.value = VoiceCaptureState(isRecording = true, elapsedMs = 0L)

        try {
            recorder.startRecording()
            val startTime = System.currentTimeMillis()
            while (isActive && !stopRequested && totalSamples < maxSamples) {
                val toRead = min(readChunk.size, maxSamples - totalSamples)
                val read = recorder.read(readChunk, 0, toRead)
                if (read > 0) {
                    System.arraycopy(readChunk, 0, captured, totalSamples, read)
                    totalSamples += read

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val norm = readChunk[i] / 32768.0
                        sumSq += norm * norm
                    }
                    val rms = sqrt(sumSq / read).toFloat().coerceIn(0f, 1f)
                    val bands = VoiceBiometricEngine.computeLiveSpectrumBands(readChunk, read)
                    val elapsed = System.currentTimeMillis() - startTime

                    _captureState.value = VoiceCaptureState(
                        isRecording = true,
                        elapsedMs = elapsed,
                        rmsLevel = rms,
                        spectrumBands = bands
                    )
                } else if (read < 0) {
                    break
                }
            }
        } catch (e: Exception) {
            _captureState.value = VoiceCaptureState(errorMessage = e.message)
            return@withContext Result.failure(e)
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {
            }
            recorder.release()
            _captureState.value = _captureState.value.copy(isRecording = false)
        }

        Result.success(captured.copyOf(totalSamples))
    }
}
