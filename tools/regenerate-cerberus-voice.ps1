# regenerate-cerberus-voice.ps1
# Regenerates Cerberus Helper's voice cues using a locally installed Windows TTS voice.
# Run this on your Windows machine (not WSL) - System.Speech is Windows-only.
#
# Usage:
#   .\tools\regenerate-cerberus-voice.ps1                    <- auto-picks the first male voice found
#   .\tools\regenerate-cerberus-voice.ps1 -VoiceName "Microsoft David Desktop"
#   .\tools\regenerate-cerberus-voice.ps1 -ListVoices        <- print installed voices and exit

param(
    [string]$VoiceName,
    [switch]$ListVoices
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer

if ($ListVoices) {
    $synth.GetInstalledVoices() | ForEach-Object {
        $i = $_.VoiceInfo
        Write-Host "$($i.Name)  [$($i.Gender), $($i.Culture)]"
    }
    return
}

if ($VoiceName) {
    $synth.SelectVoice($VoiceName)
} else {
    $male = $synth.GetInstalledVoices() |
        Where-Object { $_.VoiceInfo.Gender -eq "Male" } |
        Select-Object -First 1
    if (-not $male) {
        Write-Error "No male voice installed. Run with -ListVoices to see what's available, or pass -VoiceName explicitly."
        exit 1
    }
    $synth.SelectVoice($male.VoiceInfo.Name)
}

Write-Host "Using voice: $($synth.Voice.Name)"

# Matches the existing clips' format (16-bit PCM, mono, 44.1kHz) so AudioPlayer's
# playback behaves the same as before.
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    44100, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

$outDir = "$PSScriptRoot\..\plugin\src\main\resources\com\cerberus"

$clips = @{
    "combo-next.wav"  = "Triple next"
    "souls-next.wav"  = "Souls next"
    "lava-next.wav"   = "Lava next"
    "hold-damage.wav" = "Hold damage"
    "go-now.wav"      = "Go now"
}

foreach ($file in $clips.Keys) {
    $path = Join-Path $outDir $file
    $synth.SetOutputToWaveFile($path, $format)
    $synth.Speak($clips[$file])
    $synth.SetOutputToNull()
    Write-Host "  Wrote $file - `"$($clips[$file])`""
}

Write-Host ""
Write-Host "Done. Run .\dev-test.ps1 and trigger a Cerberus special to preview, then commit the updated .wav files."
