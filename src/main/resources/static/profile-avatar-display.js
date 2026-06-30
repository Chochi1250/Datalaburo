(function () {
    const DEFAULT_PRESET = "atlas";
    const EXTRA_OPTIONS = [
        { id: "react", label: "React", file: "/images/tech/react.png" },
        { id: "git", label: "Git", file: "/images/tech/github.png" }
    ];
    const targets = Array.from(document.querySelectorAll("[data-profile-avatar]"));

    if (!targets.length) {
        return;
    }

    function initials(name) {
        const words = String(name || "")
            .replace(/[^A-Za-z0-9 ]+/g, " ")
            .trim()
            .split(/\s+/)
            .filter(Boolean);
        if (!words.length) {
            return "DL";
        }
        if (words.length === 1) {
            return words[0].slice(0, 2).toUpperCase();
        }
        return (words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
    }

    function presetFor(target) {
        return String(target.dataset.avatarPreset || "").trim().toLowerCase();
    }

    function renderFallback(target) {
        const preset = presetFor(target) || DEFAULT_PRESET;
        target.classList.remove("is-avatar-image");
        target.dataset.avatarPreset = preset;
        target.removeAttribute("data-avatar-image");
        target.textContent = initials(target.dataset.avatarName);
    }

    function renderImage(target, option) {
        if (!option || !option.file) {
            renderFallback(target);
            return;
        }
        const image = document.createElement("img");
        image.className = "avatar-selected-image";
        image.alt = option.label || "";
        image.src = option.file;
        image.addEventListener("error", function () {
            renderFallback(target);
        }, { once: true });
        target.classList.add("is-avatar-image");
        target.removeAttribute("data-avatar-preset");
        target.dataset.avatarImage = option.id;
        target.textContent = "";
        target.appendChild(image);
    }

    targets.forEach(renderFallback);

    fetch("/images/avatars/avatars.json", { headers: { "Accept": "application/json" } })
        .then(function (response) {
            return response.ok ? response.json() : [];
        })
        .catch(function () {
            return [];
        })
        .then(function (payload) {
            const options = (Array.isArray(payload) ? payload : []).concat(EXTRA_OPTIONS);
            const byId = new Map(options
                .filter(function (option) { return option && option.id; })
                .map(function (option) { return [String(option.id).toLowerCase(), option]; }));
            targets.forEach(function (target) {
                renderImage(target, byId.get(presetFor(target)));
            });
        });
}());
