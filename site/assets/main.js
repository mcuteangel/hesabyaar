"use strict";

(() => {
  const REPO = "mcuteangel/hesabyaar";
  const FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

  const toFa = (text) =>
    String(text).replace(/\d/g, (digit) => FA_DIGITS[Number(digit)]);

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
    if (list) {
      shots.forEach((shot) => {
        const itemElement = document.createElement("li");
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
        itemElement.appendChild(fig);
        list.appendChild(itemElement);
      });
      const screenshotsSection = document.getElementById("screenshots");
      if (screenshotsSection) {
        screenshotsSection.hidden = false;
      }
    }
  } else {
    document.querySelectorAll("[data-requires-shots]").forEach((element) => {
      element.parentElement.hidden = true;
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

  const isHttps = (url) => typeof url === "string" && url.startsWith("https://");

  const buildDownloadItem = (item) => {
    const listItem = document.createElement("li");
    const link = document.createElement("a");
    link.className = "dl";
    link.href = item.asset.browser_download_url;
    const strong = document.createElement("strong");
    strong.textContent = item.abi.label;
    const span = document.createElement("span");
    span.textContent = `${item.abi.hint} · ${formatSize(item.asset.size)}`;
    link.appendChild(strong);
    link.appendChild(span);
    listItem.appendChild(link);
    return listItem;
  };

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
      const versionElement = document.getElementById("release-version");
      if (versionElement) {
        versionElement.textContent = release.tag_name;
      }

      const assets = release.assets || [];
      const validDownloads = ABIS.map((abi) => {
        const asset = assets.find((candidate) =>
          candidate.name.toLowerCase().endsWith(`-${abi.key}.apk`));
        if (asset && isHttps(asset.browser_download_url)) {
          return { abi, asset };
        }
        return null;
      }).filter(Boolean);

      if (!validDownloads.length) {
        return;
      }

      const downloadsList = document.getElementById("downloads");
      if (downloadsList) {
        downloadsList.textContent = "";
        validDownloads.forEach((item) => {
          downloadsList.appendChild(buildDownloadItem(item));
        });
      }

      const universal = validDownloads.find((item) => item.abi.key === "universal");
      const heroDownload = document.getElementById("hero-download");
      if (universal && heroDownload) {
        heroDownload.href = universal.asset.browser_download_url;
      }
    })
    .catch((error) => {
      // Keep static fallback links; log so failures are discoverable.
      console.warn("Unable to fetch latest release from GitHub API:", error);
    });
})();
