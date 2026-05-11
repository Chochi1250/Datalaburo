function setStatus(message) {
  const node = document.getElementById("status");
  if (node) {
    node.textContent = message || "";
  }
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function openLinkedInSearch() {
  const keyword = document.getElementById("search").value || "";
  const url = `https://www.linkedin.com/jobs/search/?keywords=${encodeURIComponent(keyword)}&location=Argentina`;
  window.open(url, "_blank");
  alert("Abri un job y usa la extension para capturar. Luego volve y recarga guardados.");
}

async function loadJobs() {
  setStatus("Cargando...");
  const res = await fetch("/api/jobs");
  if (!res.ok) {
    setStatus(`Error cargando jobs: ${res.status}`);
    return;
  }
  const jobs = await res.json();
  const body = document.getElementById("jobs-body");
  body.innerHTML = "";

  for (const job of jobs) {
    const tr = document.createElement("tr");
    const detailHref = `/job.html?id=${encodeURIComponent(job.id)}`;

    tr.innerHTML = `
      <td><a href="${detailHref}">${escapeHtml(job.id)}</a></td>
      <td>${escapeHtml(job.title)}</td>
      <td>${escapeHtml(job.company)}</td>
      <td>${escapeHtml(job.location)}</td>
      <td><span class="pill">${escapeHtml(job.source)}</span></td>
      <td>${job.sourceUrl ? `<a href="${escapeHtml(job.sourceUrl)}" target="_blank">abrir</a>` : ""}</td>
      <td>${escapeHtml(job.status)}</td>
      <td>${escapeHtml(job.createdAt)}</td>
    `;
    body.appendChild(tr);
  }

  setStatus(`${jobs.length} jobs guardados`);
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("btn-linkedin").addEventListener("click", openLinkedInSearch);
  document.getElementById("btn-refresh").addEventListener("click", loadJobs);
  loadJobs();
});
