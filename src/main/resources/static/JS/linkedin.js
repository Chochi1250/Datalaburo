function goToLinkedIn() {
  const keyword = document.getElementById("search").value;
  const url = `https://www.linkedin.com/jobs/search/?keywords=${keyword}&location=Argentina`;

  window.open(url, "_blank");

  alert("Una vez abierta la página, hacé click en 'Extraer datos'");
}