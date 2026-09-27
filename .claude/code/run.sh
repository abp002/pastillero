#!/bin/bash
# Contrato de verificación de Pastillero (Kotlin + Android con Gradle). exit 0 = verde.
#
#   tipos     compila todo (código y tests) y pasa el lint de Android, con los avisos como errores
#   humo      la app arranca: Robolectric abre la pantalla principal y la de alarma
#   rapido    el núcleo siempre; la suite entera si hay cambios en app/
#   test      núcleo (JUnit 5, con propiedades) + integración Android (Robolectric)
#   cov       Kover → build/lcov.info y, si hay commits y está cov-diff.py, cobertura del diff
#   sec       claves o contraseñas escritas en el código
#   e2e, mutacion: sin implementar todavía (salen 0, no es un fallo)
#
# Reglas del contrato:
#   - exit 0 = verde, cualquier otro = rojo.
#   - Un verbo que este proyecto no implementa sale 0.
#   - `tipos` y `humo` corren en cada turno: tienen que tardar segundos, no minutos.
set -u
cd "$(dirname "$0")/../.." || exit 1
verbo="${1:-test}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
g() { ./gradlew --console=plain -q "$@"; }

case "$verbo" in
  tipos)
    g :nucleo:compileTestKotlin :app:compileDebugUnitTestKotlin :app:lintDebug ;;
  humo)
    g :app:testDebugUnitTest --tests 'es.abpdev.pastillero.HumoTest' ;;
  rapido)
    if git status --porcelain -- app | grep -q .; then exec "$0" test; else g :nucleo:test; fi ;;
  test)
    g :nucleo:test :app:testDebugUnitTest ;;
  cov)
    g :nucleo:koverXmlReport :app:koverXmlReportDebug || exit 1
    mkdir -p build
    python3 .claude/code/jacoco-a-lcov.py nucleo/build/reports/kover/report.xml app/build/reports/kover/reportDebug.xml \
      > build/lcov.info || exit 1
    diff_cov="$HOME/proyectos/claude-qa/plugins/code/bin/cov-diff.py"
    if [ -f "$diff_cov" ] && git rev-parse --verify -q HEAD > /dev/null; then
      python3 "$diff_cov" --lcov build/lcov.info
      [ $? -eq 1 ] && exit 1
    fi
    exit 0 ;;
  e2e|mutacion)
    exit 0 ;;
  sec)
    # El keystore y sus contraseñas viven fuera del repo (~/.gradle/gradle.properties).
    if grep -rnIiE '(password|passwd|secret|api[_-]?key|token)[[:space:]]*[:=][[:space:]]*"[^"]{6,}"' \
        --include='*.kt' --include='*.kts' --include='*.xml' --include='*.properties' \
        --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.kotlin .; then
      exit 1
    fi
    exit 0 ;;
  *) echo "verbo desconocido: $verbo" >&2; exit 64 ;;
esac
