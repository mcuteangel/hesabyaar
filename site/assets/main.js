(() => {
  "use strict";

  const REPO = "mcuteangel/hesabyaar";
  const FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

  const toFa = (text) =>
    String(text).replace(/\d/g, (d) => FA_DIGITS[Number(d)]);

  const formatSize = (bytes) => {
    const formatted = (bytes / (1024 * 1024)).toFixed(1).replace(".", "٫");
    return `${toFa(formatted)} مگابایت`;
  };

  // Screenshots
  const shots = Array.isArray(window.HESABYAR_SCREENSHOTS)
    ? window.HESABYAR_SCREENSHOTS
    : [];
  if (shots.length) {
    const list = document.getElementById("shots-list");
    shots.forEach((shot) => {
      const li = document.createElement("li");
      const fig = document.createElement("figure");
      const img = document.createElement("img");
      img.src = shot.src;
      img.alt = shot.caption || "تصویر صفحهٔ حسابیار";
      img.loading = "lazy";
      img.width = 260;
      fig.appendChild(img);
      if (shot.caption) {
        const cap = document.createElement("figcaption");
        cap.textContent = shot.caption;
        fig.appendChild(cap);
      }
      fig.style.margin = "0";
      li.appendChild(fig);
      list.appendChild(li);
    });
    document.getElementById("screenshots").hidden = false;
  } else {
    document.querySelectorAll("[data-requires-shots]").forEach((el) => {
      el.parentElement.hidden = true;
    });
  }

  // Latest release: direct APK links. Falls back to the static
  // /releases/latest links already in the HTML when the API is unreachable.
  const ABIS = [
    { key: "universal", label: "همه‌کاره (universal)", hint: "برای همهٔ گوشی‌ها" },
    { key: "arm-v8a", label: "arm64-v8a", hint: "بیشتر گوشی‌های جدید" },
    { key: "arm-v7a", label: "armeabi-v7a", hint: "گوشی‌های قدیمی‌تر ۳۲ بیتی" },
    { key: "x86_64", label: "x86_64", hint: "شبیه‌ساز و تبلت‌های اینتل" }
  ];

  fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { Accept: "application/vnd.github+json" }
  })
    .then((res) => {
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      return res.json();
    })
    .then((release) => {
      const assets = release.assets || [];
      const found = ABIS.map((abi) => {
        const asset = assets.find((a) => a.name.endsWith(`-${abi.key}.apk`));
        return asset ? { abi, asset } : null;
      }).filter(Boolean);
      if (!found.length) {
        return;
      }

      const ul = document.getElementById("downloads");
      ul.textContent = "";
      found.forEach((item) => {
        const li = document.createElement("li");
        const a = document.createElement("a");
        a.className = "dl";
        a.href = item.asset.browser_download_url;
        const strong = document.createElement("strong");
        strong.textContent = item.abi.label;
        const span = document.createElement("span");
        span.textContent = `${item.abi.hint} · ${formatSize(item.asset.size)}`;
        a.appendChild(strong);
        a.appendChild(span);
        li.appendChild(a);
        ul.appendChild(li);
      });

      const universal = found.find((item) => item.abi.key === "universal");
      if (universal) {
        document.getElementById("hero-download").href =
          universal.asset.browser_download_url;
      }
      document.getElementById("release-version").textContent = release.tag_name;
    })
    .catch(() => {
      // Keep the static fallback links.
    });
})();
