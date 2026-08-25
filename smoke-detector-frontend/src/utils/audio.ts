let audioCtx: AudioContext | null = null

/** 播放一段短促的双音提示（浏览器可能要求先有用户手势）。 */
export function beep(): void {
  try {
    audioCtx = audioCtx || new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume()
    const oscillator = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    oscillator.type = 'square'
    oscillator.frequency.setValueAtTime(880, audioCtx.currentTime)
    oscillator.frequency.setValueAtTime(660, audioCtx.currentTime + 0.18)
    gain.gain.setValueAtTime(0.08, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.5)
    oscillator.connect(gain).connect(audioCtx.destination)
    oscillator.start()
    oscillator.stop(audioCtx.currentTime + 0.55)
  } catch (_) {
    // 浏览器可能要求用户手势后才能播放声音。
  }
}

/** 在任意用户交互时调用，用于恢复被挂起的音频上下文。 */
export function resumeAudio(): void {
  if (audioCtx?.state === 'suspended') audioCtx.resume()
}
