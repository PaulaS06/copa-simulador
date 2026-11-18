import unittest

from frontend.server import primera_diferencia, resumen_visual, validar_opciones


class ValidarOpcionesTests(unittest.TestCase):
    def test_acepta_valores_permitidos(self):
        self.assertEqual(validar_opciones({"torneos": 10, "repeticiones": 3}), (10, 3))

    def test_rechaza_limites_invalidos_y_booleanos(self):
        for datos in (
            {"torneos": 0},
            {"torneos": 1001},
            {"repeticiones": 6},
            {"torneos": True},
            [],
        ):
            with self.subTest(datos=datos), self.assertRaises(ValueError):
                validar_opciones(datos)


class CompararSalidasTests(unittest.TestCase):
    def test_ignora_nombre_de_lenguaje_y_detecta_diferencias_anidadas(self):
        python = {"lenguaje": "python", "partidos": [{"goles_local": 2}]}
        java = {"lenguaje": "java", "partidos": [{"goles_local": 2}]}
        diferente = {"lenguaje": "scala", "partidos": [{"goles_local": 1}]}
        lenguaje_anidado = {
            "lenguaje": "scala",
            "partidos": [{"goles_local": 2}],
            "metadata": {"lenguaje": "different"},
        }

        self.assertIsNone(primera_diferencia(python, java))
        self.assertEqual(
            primera_diferencia(python, diferente),
            "$.partidos[0].goles_local: valores distintos",
        )
        self.assertEqual(
            primera_diferencia({**python, "metadata": {"lenguaje": "python"}}, lenguaje_anidado),
            "$.metadata.lenguaje: valores distintos",
        )

    def test_resumen_visual_prepara_tablas_y_llave_con_nombres(self):
        salida = {
            "tablas": {
                "A": [{"equipo": 1, "pj": 3, "pg": 2, "pe": 1, "pp": 0,
                       "gf": 5, "gc": 2, "dg": 3, "puntos": 7}],
            },
            "partidos": [
                {"id": 32, "fase": "final", "local": 1, "visitante": 2,
                 "goles_local": 2, "goles_visitante": 1, "penales": None, "eventos": []},
                {"id": 1, "fase": "grupos", "local": 1, "visitante": 2,
                 "goles_local": 1, "goles_visitante": 0, "penales": None, "eventos": []},
            ],
        }

        visualizacion = resumen_visual(salida, {"1": "Colombia", "2": "Brasil"})

        self.assertEqual(visualizacion["grupos"][0]["filas"][0]["equipo"], "Colombia")
        self.assertEqual(visualizacion["llave"][0]["local"], "Colombia")
        self.assertEqual(len(visualizacion["llave"]), 1)


if __name__ == "__main__":
    unittest.main()
