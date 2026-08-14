/** 共享轮询:start 立即停止旧定时器并开启新周期;refresh 内部可条件调 stop 自动停。 */
export function usePolling(refresh: () => Promise<void>, intervalMs = 2000) {
  let timer: number | null = null

  function start() {
    stop()
    timer = window.setInterval(() => void refresh(), intervalMs)
  }

  function stop() {
    if (timer != null) {
      clearInterval(timer)
      timer = null
    }
  }

  return { start, stop }
}
