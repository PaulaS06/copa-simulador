const form = document.querySelector("#benchmark-form");
const button = document.querySelector("#run-button");
const statusText = document.querySelector("#status");
const consistency = document.querySelector("#consistency");
const championNote = document.querySelector("#champion");
const bracketSection = document.querySelector("#bracket-section");
const standingsSection = document.querySelector("#standings-section");
const languageIds = ["python", "java", "scala"];

function mostrarError(mensaje) {
  statusText.textContent = mensaje;
  statusText.classList.add("error");
}

function crearElemento(etiqueta, clase, texto) {
  const elemento = document.createElement(etiqueta);
  if (clase) elemento.className = clase;
  if (texto !== undefined) elemento.textContent = texto;
  return elemento;
}

function mostrarMediciones(lenguaje) {
  const grafica = document.querySelector(`#chart-${lenguaje.id}`);
  const cuerpoTabla = document.querySelector(`#table-${lenguaje.id}`);
  grafica.replaceChildren();
  cuerpoTabla.replaceChildren();

  const mayorTiempo = Math.max(...lenguaje.tiempos_ms, Number.EPSILON);
  lenguaje.tiempos_ms.forEach((tiempo, indice) => {
    const columna = crearElemento("div", "chart-column");
    const valor = crearElemento("span", "chart-value", tiempo.toFixed(2));
    const barra = crearElemento("span", "chart-bar");
    barra.style.height = `${Math.max(4, (tiempo / mayorTiempo) * 70)}px`;
    const etiqueta = crearElemento("span", "chart-label", `R${indice + 1}`);
    columna.append(valor, barra, etiqueta);
    grafica.append(columna);

    const fila = document.createElement("tr");
    fila.append(
      crearElemento("td", "", `Corrida ${indice + 1}`),
      crearElemento("td", "numeric-cell", `${tiempo.toFixed(2)} ms`),
    );
    cuerpoTabla.append(fila);
  });
}

function ganaLocal(partido) {
  if (partido.goles_local !== partido.goles_visitante) {
    return partido.goles_local > partido.goles_visitante;
  }
  return partido.penales && partido.penales.local > partido.penales.visitante;
}

function crearPartido(partido) {
  const localGana = ganaLocal(partido);
  const tarjeta = crearElemento("article", "match-card");
  const local = crearElemento("div", `match-team${localGana ? " match-winner" : ""}`);
  const visitante = crearElemento("div", `match-team${!localGana ? " match-winner" : ""}`);
  local.append(
    crearElemento("span", "team-name", partido.local),
    crearElemento("b", "team-score", partido.goles_local),
  );
  visitante.append(
    crearElemento("span", "team-name", partido.visitante),
    crearElemento("b", "team-score", partido.goles_visitante),
  );
  tarjeta.append(local, visitante);
  if (partido.penales) {
    tarjeta.append(crearElemento(
      "p",
      "penalty-note",
      `Penales ${partido.penales.local}–${partido.penales.visitante}`,
    ));
  }
  return tarjeta;
}

function mostrarLlave(partidos) {
  const fases = [
    ["cuartos", "Cuartos de final"],
    ["semifinal", "Semifinales"],
    ["final", "Gran final"],
    ["tercer_puesto", "Tercer puesto"],
  ];
  const contenedor = document.querySelector("#bracket");
  contenedor.replaceChildren();
  for (const [fase, titulo] of fases) {
    const partidosFase = partidos.filter((partido) => partido.fase === fase);
    if (partidosFase.length === 0) continue;
    const etapa = crearElemento("section", `bracket-stage stage-${fase}`);
    etapa.append(crearElemento("h3", "stage-title", titulo));
    for (const partido of partidosFase) etapa.append(crearPartido(partido));
    contenedor.append(etapa);
  }
  bracketSection.hidden = false;
}

function mostrarTablas(grupos) {
  const contenedor = document.querySelector("#standings");
  contenedor.replaceChildren();
  for (const grupo of grupos) {
    const tarjeta = crearElemento("article", "standing-card");
    tarjeta.append(crearElemento("h3", "standing-title", `Grupo ${grupo.grupo}`));
    const tabla = document.createElement("table");
    tabla.className = "standing-table";
    const cabeza = document.createElement("thead");
    const encabezado = document.createElement("tr");
    for (const columna of ["#", "Selección", "PJ", "DG", "PTS"]) {
      encabezado.append(crearElemento("th", "", columna));
    }
    cabeza.append(encabezado);
    const cuerpo = document.createElement("tbody");
    for (const fila of grupo.filas) {
      const tr = crearElemento("tr", fila.posicion <= 2 ? "qualifier" : "");
      for (const valor of [fila.posicion, fila.equipo, fila.pj, fila.dg, fila.puntos]) {
        tr.append(crearElemento("td", typeof valor === "number" ? "numeric-cell" : "", valor));
      }
      cuerpo.append(tr);
    }
    tabla.append(cabeza, cuerpo);
    tarjeta.append(tabla);
    contenedor.append(tarjeta);
  }
  standingsSection.hidden = false;
}

function mostrarResultados(datos) {
  const masRapido = Math.min(...datos.lenguajes.map((lenguaje) => lenguaje.mediana_ms));

  for (const lenguaje of datos.lenguajes) {
    const tarjeta = document.querySelector(`[data-language="${lenguaje.id}"]`);
    const esMasRapido = lenguaje.mediana_ms === masRapido;
    tarjeta.classList.toggle("is-fastest", esMasRapido);
    document.querySelector(`#time-${lenguaje.id}`).innerHTML =
      `${lenguaje.mediana_ms.toFixed(2)}<small> ms</small>`;
    const proporcion =
      (Math.max(masRapido, Number.EPSILON) / Math.max(lenguaje.mediana_ms, Number.EPSILON)) * 100;
    document.querySelector(`#bar-${lenguaje.id}`).style.width = `${proporcion}%`;
    document.querySelector(`#speed-${lenguaje.id}`).textContent = esMasRapido
      ? "Más rápido"
      : `${(lenguaje.mediana_ms / Math.max(masRapido, Number.EPSILON)).toFixed(2)}× el tiempo del más rápido`;
    mostrarMediciones(lenguaje);
  }

  consistency.hidden = false;
  consistency.classList.toggle("mismatch", !datos.salidas_iguales);
  consistency.textContent = datos.salidas_iguales
    ? "✓ Los tres simuladores produjeron el mismo resultado. Se ignoró únicamente el nombre del lenguaje."
    : `⚠ Las salidas no coinciden: ${datos.diferencia}`;
  championNote.hidden = false;
  championNote.textContent =
    `Campeón del primer torneo: ${datos.lenguajes[0].campeon} · ` +
    `${datos.torneos} torneo(s) por ejecución · ${datos.repeticiones} repetición(es)`;
  mostrarLlave(datos.llave);
  mostrarTablas(datos.grupos);
  statusText.textContent = "Comparación completada.";
  statusText.classList.remove("error");
}

function limpiarResultados() {
  consistency.hidden = true;
  championNote.hidden = true;
  bracketSection.hidden = true;
  standingsSection.hidden = true;
  for (const id of languageIds) {
    document.querySelector(`[data-language="${id}"]`).classList.remove("is-fastest");
    document.querySelector(`#time-${id}`).innerHTML = `—<small> ms</small>`;
    document.querySelector(`#bar-${id}`).style.width = "0";
    document.querySelector(`#speed-${id}`).textContent = "Midiendo…";
    document.querySelector(`#chart-${id}`).replaceChildren(
      crearElemento("p", "chart-empty", "Midiendo tiempos…"),
    );
    document.querySelector(`#table-${id}`).replaceChildren();
  }
}

form.addEventListener("submit", async (evento) => {
  evento.preventDefault();
  button.disabled = true;
  button.classList.add("is-loading");
  statusText.classList.remove("error");
  statusText.textContent = "Jugando la copa… la primera compilación puede tardar un momento.";
  limpiarResultados();

  try {
    const respuesta = await fetch("/api/benchmark", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        torneos: Number(form.elements.torneos.value),
        repeticiones: Number(form.elements.repeticiones.value),
      }),
    });
    const datos = await respuesta.json();
    if (!respuesta.ok) throw new Error(datos.error || "No fue posible ejecutar la comparación.");
    mostrarResultados(datos);
  } catch (error) {
    mostrarError(error.message || "No fue posible conectar con el servidor local.");
    for (const id of languageIds) {
      document.querySelector(`#time-${id}`).innerHTML = `—<small> ms</small>`;
      document.querySelector(`#bar-${id}`).style.width = "0";
      document.querySelector(`#speed-${id}`).textContent = "Sin resultados";
      document.querySelector(`#chart-${id}`).replaceChildren(
        crearElemento("p", "chart-empty", "La prueba no pudo completarse"),
      );
    }
  } finally {
    button.disabled = false;
    button.classList.remove("is-loading");
  }
});
