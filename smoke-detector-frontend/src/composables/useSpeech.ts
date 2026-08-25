import { ref } from 'vue'

type Recognition = {
  lang: string
  interimResults: boolean
  maxAlternatives: number
  onresult: ((event: { results: { [index: number]: { [index: number]: { transcript: string } } } }) => void) | null
  onerror: (() => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
}

type SpeechRecognitionCtor = new () => Recognition

/**
 * 封装浏览器语音识别（Web Speech API）。仅支持 Chrome / Edge。
 * 识别成功回调文本，出错回调提示信息。
 */
export function useSpeech(onResult: (text: string) => void, onError: (message: string) => void) {
  const listening = ref(false)
  const win = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor
    webkitSpeechRecognition?: SpeechRecognitionCtor
  }
  const supported = Boolean(win.SpeechRecognition || win.webkitSpeechRecognition)

  let recognition: Recognition | null = null

  function toggle(): void {
    if (!supported) {
      onError('当前浏览器不支持语音输入，请使用 Chrome 或 Edge，或直接输入问题。')
      return
    }
    if (recognition) {
      recognition.stop()
      return
    }
    const SpeechRecognition = win.SpeechRecognition || win.webkitSpeechRecognition!
    recognition = new SpeechRecognition()
    recognition.lang = 'zh-CN'
    recognition.interimResults = false
    recognition.maxAlternatives = 1
    listening.value = true
    recognition.onresult = (event) => {
      const text = event.results[0][0].transcript.trim()
      if (text) onResult(text)
    }
    recognition.onerror = () => onError('语音识别出错，请重试或直接输入问题。')
    recognition.onend = () => {
      listening.value = false
      recognition = null
    }
    recognition.start()
  }

  return { listening, supported, toggle }
}
