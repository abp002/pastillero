#!/usr/bin/env python3
"""Pasa los informes XML de Kover (formato JaCoCo) a lcov, que es lo que lee cov-diff.py.

    jacoco-a-lcov.py informe1.xml [informe2.xml ...] > build/lcov.info

Las rutas salen relativas a la raíz del repo, buscando el fichero en las raíces de fuentes.
El resumen por fichero va a stderr para no mezclarse con el lcov.
"""
import os
import sys
import xml.etree.ElementTree as ET

RAICES = ["nucleo/src/main/kotlin", "app/src/main/kotlin"]


def ruta_real(paquete, nombre):
    for raiz in RAICES:
        candidata = os.path.join(raiz, paquete, nombre)
        if os.path.exists(candidata):
            return candidata
    return None


def main(informes):
    total = cubiertas = 0
    for informe in informes:
        if not os.path.exists(informe):
            print(f"no existe {informe}", file=sys.stderr)
            return 2
        for paquete in ET.parse(informe).getroot().iter("package"):
            for fuente in paquete.findall("sourcefile"):
                ruta = ruta_real(paquete.get("name"), fuente.get("name"))
                if ruta is None:
                    continue
                print(f"SF:{ruta}")
                for linea in fuente.findall("line"):
                    cubierta = int(linea.get("ci", "0")) > 0
                    if int(linea.get("ci", "0")) + int(linea.get("mi", "0")) == 0:
                        continue
                    total += 1
                    cubiertas += cubierta
                    print(f"DA:{linea.get('nr')},{1 if cubierta else 0}")
                print("end_of_record")
    if total:
        print(f"cobertura global de líneas: {cubiertas}/{total} ({100 * cubiertas / total:.1f} %)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
