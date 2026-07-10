(function () {
  const ROLE_FAMILIES = {
    backend: {
      aliases: ["backend", "back end", "backend apis", "backend api", "dotnet backend", ".net backend", "backend java"],
    },
    frontend: {
      aliases: ["frontend", "front end", "web", "ux ui", "ui", "web frontend"],
    },
    fullstack: {
      aliases: ["fullstack", "full stack", "dotnet fullstack", ".net full stack", ".net fullstack"],
    },
    data: {
      aliases: ["data", "data bi", "bi", "analytics", "base de datos", "database", "ai ml", "machine learning"],
    },
    cloud: {
      aliases: ["cloud", "devops", "cloud devops", "cloud/devops", "infra", "infraestructura", "sre"],
    },
    support: {
      aliases: ["support", "soporte", "soporte it", "it support", "app support", "support it", "infra support"],
    },
    qa: {
      aliases: ["qa", "testing", "tester", "quality assurance"],
    },
  };

  const SENIORITY_FAMILIES = {
    junior: {
      aliases: ["trainee", "junior", "jr", "entry", "entry level"],
    },
    mid: {
      aliases: ["mid", "semi senior", "semisenior", "ssr"],
    },
    senior: {
      aliases: ["senior", "sr"],
    },
    lead: {
      aliases: ["lead", "manager", "management", "lider", "liderazgo", "principal", "staff"],
    },
  };

  const WORK_FAMILIES = {
    remote: {
      aliases: ["remoto", "remote"],
    },
    hybrid: {
      aliases: ["hibrido", "hybrid"],
    },
    onsite: {
      aliases: ["presencial", "onsite", "on site"],
    },
    fulltime: {
      aliases: ["full time", "full-time", "fulltime", "tiempo completo"],
    },
    contract: {
      aliases: ["contrato", "contract", "contractor"],
    },
  };

  const FLOW_FAMILIES = {
    cv: {
      aliases: ["cv", "cv rapido", "texto libre", "pegar cv", "accion rapida"],
    },
    profile: {
      aliases: ["perfil", "perfil local", "perfil guardado", "perfil existente"],
    },
    ready: {
      aliases: ["estado visible", "listo", "ready", "visible", "preparado", "procesado"],
    },
    continuity: {
      aliases: ["continuidad", "continuar", "continuar analisis"],
    },
    active: {
      aliases: ["activo", "seleccionado"],
    },
    focused: {
      aliases: ["focused", "enfocado", "foco", "busqueda enfocada"],
    },
    balanced: {
      aliases: ["balanced", "balanceado", "equilibrado"],
    },
    exploratory: {
      aliases: ["exploratory", "exploratorio", "exploracion", "exploracion amplia"],
    },
  };

  const REGISTRIES = {
    role: ROLE_FAMILIES,
    seniority: SENIORITY_FAMILIES,
    work: WORK_FAMILIES,
    flow: FLOW_FAMILIES,
  };

  function normalize(value) {
    return String(value || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .replace(/[_./-]+/g, " ")
      .replace(/[^a-z0-9+#\s-]+/g, " ")
      .replace(/\s+/g, " ")
      .trim();
  }

  function matchesAlias(normalizedValue, alias) {
    const normalizedAlias = normalize(alias);
    return normalizedValue === normalizedAlias || normalizedValue.includes(normalizedAlias);
  }

  function resolve(kind, value) {
    const registry = REGISTRIES[kind] || {};
    const normalizedValue = normalize(value);
    const family = Object.keys(registry).find(function (key) {
      return registry[key].aliases.some(function (alias) {
        return matchesAlias(normalizedValue, alias);
      });
    });
    return family || "generic";
  }

  function clearFamilyClasses(element) {
    Array.from(element.classList).forEach(function (className) {
      if (/^dl-badge--(role|seniority|work|flow)-/.test(className)) {
        element.classList.remove(className);
      }
    });
  }

  function apply(element, kind, value) {
    if (!element || !kind) return null;
    const family = resolve(kind, value || element.textContent);
    clearFamilyClasses(element);
    element.classList.add("dl-badge", "dl-badge--" + kind, "dl-badge--" + kind + "-" + family);
    element.dataset.badgeKind = kind;
    element.dataset.badgeFamily = family;
    return family;
  }

  function enhanceAll(root) {
    const scope = root || document;

    scope.querySelectorAll("[data-badge-kind]").forEach(function (element) {
      apply(element, element.dataset.badgeKind, element.dataset.badgeValue || element.textContent);
    });

    scope.querySelectorAll(".page-vector-match .vector-profile-chip:not(.tech-badge)").forEach(function (element) {
      const kind = element.classList.contains("vector-profile-chip-soft") ? "seniority" : "role";
      apply(element, kind, element.textContent);
    });
  }

  window.DATALABURO_VISUAL_BADGES = {
    roles: ROLE_FAMILIES,
    seniority: SENIORITY_FAMILIES,
    work: WORK_FAMILIES,
    flow: FLOW_FAMILIES,
    normalize: normalize,
    resolve: resolve,
    apply: apply,
    enhanceAll: enhanceAll,
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      enhanceAll(document);
    });
  } else {
    enhanceAll(document);
  }
}());
