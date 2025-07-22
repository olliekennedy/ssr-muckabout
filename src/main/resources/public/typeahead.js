document.addEventListener("DOMContentLoaded", () => {
  const setupTypeahead = (inputId, suggestionsId) => {
    const input = document.getElementById(inputId);
    const suggestions = document.getElementById(suggestionsId);
    let stations = [];

    fetch("/stations")
      .then((res) => res.json())
      .then((data) => {
        stations = data;
      });

    input.addEventListener("input", () => {
      const query = input.value.toLowerCase();
      suggestions.innerHTML = "";

      if (!query) return;

      const matches = stations.filter((station) =>
        station.name.toLowerCase().includes(query) ||
        station.code.toLowerCase().includes(query)
      ).slice(0, 10);

      matches.forEach((station) => {
        const div = document.createElement("div");
        div.textContent = `${station.name} [${station.code}]`;
        div.addEventListener("click", () => {
          input.value = station.code; // only submit the 3ALPHA code
          suggestions.innerHTML = "";
        });
        suggestions.appendChild(div);
      });
    });

    document.addEventListener("click", (e) => {
      if (!suggestions.contains(e.target) && e.target !== input) {
        suggestions.innerHTML = "";
      }
    });
  };

  setupTypeahead("from-input", "from-suggestions");
  setupTypeahead("to-input", "to-suggestions");
});
