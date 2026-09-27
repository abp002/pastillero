#!/bin/bash
# Publica una versión: verifica, sube el número, compila el APK firmado y crea la release en GitHub.
# Obtainium, en el móvil donde está instalada, la ve y la instala sola.
#
#   scripts/publicar.sh 0.1.1 "Qué cambia, en una línea"
#
# No publica nada si la suite o el lint están en rojo, si hay cambios sin commit o si la
# cuenta activa de gh no es la dueña del repo.
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?Uso: scripts/publicar.sh X.Y.Z \"qué cambia\"}"
notas="${2:?Falta una línea con qué cambia}"
cuenta_repo="abp002"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

fallo() { echo "✗ $*" >&2; exit 1; }
# Si algo falla después de subir el número, se deja como estaba.
subido=0
deshacer() { [ "$subido" = 1 ] && git checkout -- gradle.properties; return 0; }
trap deshacer EXIT

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fallo "La versión tiene que ser X.Y.Z"
[ -z "$(git status --porcelain)" ] || fallo "Hay cambios sin commit: se publica exactamente lo que está en git"
[ "$(git branch --show-current)" = "main" ] || fallo "Publica desde main"
[ "$(gh api user --jq .login)" = "$cuenta_repo" ] || fallo "gh no está en $cuenta_repo: gh auth switch -u $cuenta_repo"
git rev-parse -q --verify "refs/tags/v$version" > /dev/null && fallo "La v$version ya existe"

actual=$(sed -n 's/^pastilleroVersion=//p' gradle.properties)
if [ "$version" != "$actual" ]; then
  # Android no instala una versión con número menor: tiene que crecer siempre.
  [ "$(printf '%s\n%s\n' "$actual" "$version" | sort -V | tail -1)" = "$version" ] || fallo "$version no es mayor que $actual"
fi

echo "→ Verificando (tipos + lint + suite)…"
.claude/code/run.sh tipos || fallo "tipos/lint en rojo"
.claude/code/run.sh test || fallo "suite en rojo"

if [ "$version" != "$actual" ]; then
  sed -i '' "s/^pastilleroVersion=.*/pastilleroVersion=$version/" gradle.properties
  subido=1
fi

echo "→ Compilando el APK firmado…"
./gradlew --console=plain -q :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
herramientas=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
"$herramientas/apksigner" verify "$apk" || fallo "El APK no está firmado (¿falta la clave en ~/.gradle/gradle.properties?)"
dentro=$("$herramientas/aapt2" dump badging "$apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
[ "$dentro" = "$version" ] || fallo "El APK dice $dentro y no $version"
mkdir -p build
cp "$apk" "build/pastillero-$version.apk"

echo "→ Publicando v$version…"
if [ "$version" != "$actual" ]; then
  git commit -q -am "Versión $version: $notas"
  subido=0
fi
git tag -a "v$version" -m "$notas"
git push -q origin main "v$version"
gh release create "v$version" "build/pastillero-$version.apk" --title "$version" --notes "$notas"
echo "✓ Publicada. Obtainium la instalará en su próxima comprobación."
