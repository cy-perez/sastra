/**
 * Arranca el backend para las pruebas de extremo a extremo completas y deja su
 * registro en un archivo.
 *
 * <p>El archivo no es un detalle de comodidad: es como la prueba recupera el
 * token de verificacion. Con `MAIL_PROVIDER=console` el adaptador de consola
 * imprime el enlace entero (ADR-0012), y leerlo del registro es la unica forma de
 * completar el flujo sin inventar un endpoint que exista solo para las pruebas.
 * Un endpoint asi seria codigo de produccion que entrega tokens, y ninguna
 * comodidad de pruebas paga eso.
 *
 * <p>Hace falta un lanzador y no basta una redireccion en la linea de comandos
 * porque `webServer` de Playwright ejecuta a traves del shell del sistema, y la
 * sintaxis de redireccion no es la misma en cmd y en sh. Aqui se hace igual en
 * los dos.
 */
import { spawn, spawnSync } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const AQUI = dirname(fileURLToPath(import.meta.url));
const RAIZ = resolve(AQUI, '..', '..');
const LIBRERIAS = join(RAIZ, 'backend', 'bootstrap', 'build', 'libs');

export const ARCHIVO_DE_REGISTRO = join(AQUI, '.registro-backend.log');

function jarDelBackend() {
  if (!existsSync(LIBRERIAS)) {
    return null;
  }
  // El `-plain.jar` que produce la tarea `jar` no es ejecutable: lleva las clases
  // pero no las dependencias. El que arranca es el de `bootJar`.
  const candidatos = readdirSync(LIBRERIAS).filter(
    (nombre) => nombre.endsWith('.jar') && !nombre.endsWith('-plain.jar'),
  );
  return candidatos.length > 0 ? join(LIBRERIAS, candidatos[0]) : null;
}

/**
 * El backend se compila con Java 25 y no arranca con un runtime anterior.
 *
 * <p>Buscarlo hace falta porque el `java` del PATH no tiene por que ser el bueno:
 * Gradle usa su toolchain, que se descarga sola a `~/.gradle/jdks`, asi que en una
 * maquina con Java 21 instalado el proyecto compila perfectamente y este jar no
 * arranca. Sin esta busqueda el fallo es un `UnsupportedClassVersionError` en
 * mitad del registro de Playwright, que no dice que hacer.
 */
const VERSION_MINIMA = 25;

function versionDe(ejecutable) {
  const salida = spawnSync(ejecutable, ['-version'], { encoding: 'utf8' });
  if (salida.error) {
    return null;
  }
  // `java -version` escribe en la salida de error, no en la estandar.
  const texto = `${salida.stderr ?? ''}${salida.stdout ?? ''}`;
  const encontrado = texto.match(/version "(\d+)/);
  return encontrado ? Number(encontrado[1]) : null;
}

function candidatosDeJava() {
  const candidatos = [];

  if (process.env['JAVA_HOME']) {
    candidatos.push(join(process.env['JAVA_HOME'], 'bin', 'java'));
  }

  // Las toolchain que Gradle se descarga solo.
  const jdksDeGradle = join(homedir(), '.gradle', 'jdks');
  if (existsSync(jdksDeGradle)) {
    for (const carpeta of readdirSync(jdksDeGradle)) {
      candidatos.push(join(jdksDeGradle, carpeta, 'bin', 'java'));
      // Algunas distribuciones traen un nivel mas de carpeta dentro del zip.
      const dentro = join(jdksDeGradle, carpeta);
      try {
        for (const subcarpeta of readdirSync(dentro)) {
          candidatos.push(join(dentro, subcarpeta, 'bin', 'java'));
        }
      } catch {
        // No era una carpeta: se ignora.
      }
    }
  }

  candidatos.push('java');
  return candidatos;
}

function javaCompatible() {
  for (const candidato of candidatosDeJava()) {
    const version = versionDe(candidato);
    if (version !== null && version >= VERSION_MINIMA) {
      return candidato;
    }
  }
  return null;
}

const jar = jarDelBackend();

if (!jar) {
  console.error(
    '\nNo hay ningun jar ejecutable del backend en backend/bootstrap/build/libs.\n' +
      'Se construye con:\n\n' +
      '  cd backend && ./gradlew :bootstrap:bootJar\n\n' +
      'Las pruebas de extremo a extremo completas arrancan el backend de verdad,\n' +
      'asi que necesitan el artefacto empaquetado y PostgreSQL escuchando.\n' +
      'La base se levanta con: docker compose up -d postgres\n',
  );
  process.exit(1);
}

const java = javaCompatible();

if (!java) {
  console.error(
    `\nNo se encontro ningun Java ${VERSION_MINIMA} o superior con el que arrancar el backend.\n` +
      'El proyecto compila con la toolchain de Gradle, que puede no ser el java del PATH.\n' +
      'Se busco en JAVA_HOME, en ~/.gradle/jdks y en el PATH.\n\n' +
      'La forma mas rapida de tenerlo es dejar que Gradle lo descargue:\n\n' +
      '  cd backend && ./gradlew :bootstrap:bootJar\n',
  );
  process.exit(1);
}

// Un registro de una ejecucion anterior haria que la prueba encontrara el enlace
// equivocado: el de un token que ya no existe.
rmSync(ARCHIVO_DE_REGISTRO, { force: true });
mkdirSync(AQUI, { recursive: true });

const registro = createWriteStream(ARCHIVO_DE_REGISTRO, { flags: 'a' });

const proceso = spawn(java, ['-jar', jar], { stdio: ['ignore', 'pipe', 'pipe'] });

proceso.stdout.pipe(registro);
proceso.stderr.pipe(registro);

// Tambien a la salida de este proceso: si el backend no arranca, Playwright agota
// el tiempo de espera y lo unico que ayuda es ver por que.
proceso.stdout.pipe(process.stdout);
proceso.stderr.pipe(process.stderr);

const despedir = () => proceso.kill();
process.on('SIGTERM', despedir);
process.on('SIGINT', despedir);

proceso.on('exit', (codigo) => process.exit(codigo ?? 0));
