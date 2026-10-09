/* jshint esversion: 6, browser: true */
"use strict";

(() => {
  const REPO = "mcuteangel/hesabyaar";
  const FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

  const toFa = (text) =>
    String(text).replace(/\d/g, (digit) => FA_DIGITS[Number(digit)]);

  const formatKilobytes = (kb) =>
    kb >= 1023.95
      ? `${toFa("۱٫۰")} مگابایت`
      : `${toFa(kb.toFixed(1).replace(".", "٫"))} کیلوبایت`;

  const formatPositiveSize = (bytes) => {
    if (bytes < 1024) {
      return `${toFa(bytes)} بایت`;
    }
    if (bytes < 1024 * 1024) {
      return formatKilobytes(bytes / 1024);
    }
    const mb = bytes / (1024 * 1024);
    return `${toFa(mb.toFixed(1).replace(".", "٫"))} مگابایت`;
  };

  const formatSize = (bytes) =>
    Number(bytes) > 0 ? formatPositiveSize(bytes) : "";

  // Screenshots
  const buildScreenshotItem = (shot) => {
    const itemElement = document.createElement("li");
    const figureElement = document.createElement("figure");
    const imageElement = document.createElement("img");
    imageElement.src = shot.src;
    imageElement.alt = shot.caption || "تصویر صفحهٔ حسابیار";
    imageElement.loading = "lazy";
    imageElement.width = 260;
    figureElement.appendChild(imageElement);
    if (shot.caption) {
      const captionElement = document.createElement("figcaption");
      captionElement.textContent = shot.caption;
      figureElement.appendChild(captionElement);
    }
    figureElement.style.margin = "0";
    itemElement.appendChild(figureElement);
    return itemElement;
  };

  const renderScreenshots = (shots) => {
    const list = document.getElementById("shots-list");
    if (!list) {
      return;
    }
    shots.forEach((shot) => {
      list.appendChild(buildScreenshotItem(shot));
    });
    const screenshotsSection = document.getElementById("screenshots");
    if (screenshotsSection) {
      screenshotsSection.hidden = false;
    }
  };

  const hideScreenshotsPlaceholders = () => {
    document.querySelectorAll("[data-requires-shots]").forEach((element) => {
      element.parentElement.hidden = true;
    });
  };

  const initScreenshots = () => {
    const shots = Array.isArray(window.HESABYAR_SCREENSHOTS)
      ? window.HESABYAR_SCREENSHOTS
      : [];
    if (shots.length) {
      renderScreenshots(shots);
    } else {
      hideScreenshotsPlaceholders();
    }
  };

  initScreenshots();

  // Latest release: direct APK links. Falls back to the static
  // /releases/latest links already in the HTML when the API is unreachable.
  const ABIS = [
    { key: "universal", label: "همه‌کاره (universal)", hint: "برای همهٔ گوشی‌ها" },
    { key: "arm-v8a", label: "arm64-v8a", hint: "بیشتر گوشی‌های جدید" },
    { key: "arm-v7a", label: "armeabi-v7a", hint: "گوشی‌های قدیمی‌تر ۳۲ بیتی" },
    { key: "x86_64", label: "x86_64", hint: "شبیه‌ساز و تبلت‌های اینتل" }
  ];

  const isHttps = (url) => typeof url === "string" && url.startsWith("https://");

  const buildDownloadItem = (downloadItem) => {
    const listItem = document.createElement("li");
    const linkElement = document.createElement("a");
    linkElement.className = "dl";
    linkElement.href = downloadItem.asset.browser_download_url;
    const labelStrong = document.createElement("strong");
    labelStrong.textContent = downloadItem.abi.label;
    const detailSpan = document.createElement("span");
    detailSpan.textContent = `${downloadItem.abi.hint} · ${formatSize(downloadItem.asset.size)}`;
    linkElement.appendChild(labelStrong);
    linkElement.appendChild(detailSpan);
    listItem.appendChild(linkElement);
    return listItem;
  };

  const updateVersion = (release) => {
    const versionElement = document.getElementById("release-version");
    if (versionElement && typeof release.tag_name === "string" && release.tag_name.trim().length > 0) {
      versionElement.textContent = release.tag_name;
    }
  };

  const findMatchingAsset = (assets, key) =>
    assets.find((candidateAsset) =>
      candidateAsset.name.toLowerCase().endsWith(`-${key}.apk`));

  const filterValidDownload = (assets, abiItem) => {
    const asset = findMatchingAsset(assets, abiItem.key);
    if (!asset || !isHttps(asset.browser_download_url)) {
      return null;
    }
    return { abi: abiItem, asset };
  };

  const getValidDownloads = (assets) =>
    ABIS.map((abiItem) => filterValidDownload(assets, abiItem)).filter(Boolean);

  const renderDownloads = (validDownloads) => {
    const downloadsList = document.getElementById("downloads");
    if (!downloadsList) {
      return;
    }
    downloadsList.textContent = "";
    validDownloads.forEach((downloadItem) => {
      downloadsList.appendChild(buildDownloadItem(downloadItem));
    });
  };

  const updateHeroDownload = (validDownloads) => {
    const universal = validDownloads.find((downloadItem) => downloadItem.abi.key === "universal");
    const heroDownload = document.getElementById("hero-download");
    if (universal && heroDownload) {
      heroDownload.href = universal.asset.browser_download_url;
    }
  };

  const handleRelease = (release) => {
    updateVersion(release);
    const assets = Array.isArray(release.assets) ? release.assets : [];
    const validDownloads = getValidDownloads(assets);
    if (!validDownloads.length) {
      return;
    }
    renderDownloads(validDownloads);
    updateHeroDownload(validDownloads);
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
    .then(handleRelease)
    .catch((error) => {
      // Keep static fallback links; log so failures are discoverable.
      console.warn("Unable to fetch latest release from GitHub API:", error);
    });
})();
