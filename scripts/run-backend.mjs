// Starts the Spring Boot backend via the Maven wrapper, cross-platform.
// Used by `pnpm run dev` (see package.json) so one command boots both apps.
import { readFileSync, existsSync } from 'node:fs'
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendDir = path.join(root, 'backend')
const isWindows = process.platform === 'win32'

// Load a gitignored repo-root .env (if present) into process.env so the
// backend picks up shared secrets (SMTP, etc.) without per-project editing.
// Existing environment variables always win over the .env values.
function loadDotEnv(file) {
  if (!existsSync(file)) return
  for (const raw of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#') || !line.includes('=')) continue
    const eq = line.indexOf('=')
    const key = line.slice(0, eq).trim()
    let value = line.slice(eq + 1).trim()
    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1)
    if (process.env[key] === undefined) process.env[key] = value
  }
}

loadDotEnv(path.join(root, '.env'))

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
