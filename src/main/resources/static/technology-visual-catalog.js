(function () {
	"use strict";

	const cache = new Map();
	const pendingLabels = new Map();
	let flushTimer = null;

	function normalizedRequestKey(value) {
		return String(value || "").trim().toLocaleLowerCase();
	}

	function contrastColor(hexColor) {
		const color = String(hexColor || "#334155").replace("#", "");
		const normalized = color.length === 3
			? color.split("").map(function (value) { return value + value; }).join("")
			: color.padEnd(6, "0").slice(0, 6);
		const red = Number.parseInt(normalized.slice(0, 2), 16);
		const green = Number.parseInt(normalized.slice(2, 4), 16);
		const blue = Number.parseInt(normalized.slice(4, 6), 16);
		const brightness = (red * 299 + green * 587 + blue * 114) / 1000;
		return brightness > 172 ? "#0F172A" : "#FFFFFF";
	}

	function removeMedia(badge) {
		badge.querySelectorAll(":scope > .tech-badge-media").forEach(function (media) {
			media.remove();
		});
		if (badge.classList.contains("tech-badge")) {
			badge.classList.add("is-text-only");
			badge.dataset.techHasImage = "false";
		}
	}

	function applyVisual(badge, visual) {
		if (!badge || !visual) {
			return;
		}
		const accent = visual.accentColor || "#334155";
		badge.style.setProperty("--tech-accent", accent);
		badge.style.setProperty("--tech-contrast", contrastColor(accent));
		badge.style.setProperty("--skill-dot", accent);
		badge.dataset.techVisualState = "resolved";

		if (!badge.classList.contains("tech-badge")) {
			return;
		}
		removeMedia(badge);
		if (!visual.hasImage || !visual.imagePath) {
			return;
		}

		const media = document.createElement("span");
		media.className = "tech-badge-media is-loading";
		media.setAttribute("aria-hidden", "true");
		const image = document.createElement("img");
		image.className = "tech-badge-logo";
		image.alt = "";
		image.decoding = "async";
		image.dataset.logoKind = visual.imagePath.toLowerCase().endsWith(".svg") ? "svg" : "raster";
		image.addEventListener("load", function () {
			media.classList.remove("is-loading");
			badge.classList.remove("is-text-only");
			badge.dataset.techHasImage = "true";
		}, { once: true });
		image.addEventListener("error", function () {
			media.remove();
			badge.classList.add("is-text-only");
			badge.dataset.techHasImage = "false";
		}, { once: true });
		image.src = visual.imagePath;
		media.appendChild(image);
		badge.insertBefore(media, badge.firstChild);
	}

	function badgesForLabel(label) {
		return pendingLabels.get(normalizedRequestKey(label)) || [];
	}

	async function fetchChunk(labels) {
		const params = new URLSearchParams();
		labels.forEach(function (label) { params.append("name", label); });
		const response = await fetch("/api/technology-visuals?" + params.toString(), {
			headers: { "Accept": "application/json" }
		});
		if (!response.ok) {
			throw new Error("Technology Visual Catalog returned " + response.status);
		}
		return response.json();
	}

	async function flush() {
		flushTimer = null;
		const labels = Array.from(pendingLabels.values()).map(function (badges) {
			return badges[0].dataset.tech;
		});
		if (!labels.length) {
			return;
		}

		try {
			for (let index = 0; index < labels.length; index += 40) {
				const visuals = await fetchChunk(labels.slice(index, index + 40));
				visuals.forEach(function (visual) {
					const key = normalizedRequestKey(visual.requestedName);
					cache.set(key, visual);
					badgesForLabel(visual.requestedName).forEach(function (badge) {
						applyVisual(badge, visual);
					});
					pendingLabels.delete(key);
				});
			}
		} catch (error) {
			pendingLabels.forEach(function (badges) {
				badges.forEach(function (badge) {
					badge.dataset.techVisualState = "fallback";
					removeMedia(badge);
				});
			});
			pendingLabels.clear();
		}
	}

	function queueBadge(badge) {
		const label = String(badge.dataset.tech || "").trim();
		if (!label || badge.dataset.techVisualState === "resolved") {
			return;
		}
		const key = normalizedRequestKey(label);
		if (cache.has(key)) {
			applyVisual(badge, cache.get(key));
			return;
		}
		const badges = pendingLabels.get(key) || [];
		if (!badges.includes(badge)) {
			badges.push(badge);
			pendingLabels.set(key, badges);
		}
		badge.dataset.techVisualState = "pending";
		if (!flushTimer) {
			flushTimer = window.setTimeout(flush, 0);
		}
	}

	function enhance(root) {
		const target = root || document;
		if (target.matches && target.matches("[data-tech]")) {
			queueBadge(target);
		}
		if (target.querySelectorAll) {
			target.querySelectorAll("[data-tech]").forEach(queueBadge);
		}
	}

	function observe() {
		const observer = new MutationObserver(function (mutations) {
			mutations.forEach(function (mutation) {
				mutation.addedNodes.forEach(function (node) {
					if (node.nodeType === Node.ELEMENT_NODE) {
						enhance(node);
					}
				});
			});
		});
		observer.observe(document.body, { childList: true, subtree: true });
	}

	window.DataLaburoTechnologyVisualCatalog = { enhance: enhance };
	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", function () {
			enhance(document);
			observe();
		}, { once: true });
	} else {
		enhance(document);
		observe();
	}
}());
