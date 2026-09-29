// Starts the Spring Boot backend via the Maven wrapper, cross-platform.
// Used by `pnpm run dev` (see package.json) so one command boots both apps.
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendDir = path.join(root, 'backend')
const isWindows = process.platform === 'win32'

// On Windows a .cmd wrapper must be launched through cmd.exe. Invoking it
// directly (rather than with `shell: true` + args) avoids Node's DEP0190.
const command = isWindows ? 'cmd.exe' : './mvnw'
const args = isWindows ? ['/c', 'mvnw.cmd', 'spring-boot:run'] : ['spring-boot:run']

const child = spawn(command, args, {
  cwd: backendDir,
  stdio: 'inherit',
})

child.on('error', (error) => {
  console.error(`\nFailed to start the backend: ${error.message}`)
  console.error('Make sure Java 21+ is installed and backend/mvnw(.cmd) exists.\n')
  process.exit(1)
})

child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  else process.exit(code ?? 0)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
