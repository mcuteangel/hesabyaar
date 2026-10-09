(function () {
  "use strict";

  var REPO = "mcuteangel/hesabyaar";
  var FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

  function toFa(text) {
    return String(text).replace(/\d/g, function (d) {
      return FA_DIGITS[Number(d)];
    });
  }

  function formatSize(bytes) {
    return toFa((bytes / (1024 * 1024)).toFixed(1)) + " مگابایت";
  }

  // Screenshots
  var shots = window.HESABYAR_SCREENSHOTS || [];
  if (shots.length) {
    var list = document.getElementById("shots-list");
    shots.forEach(function (shot) {
      var li = document.createElement("li");
      var fig = document.createElement("figure");
      var img = document.createElement("img");
      img.src = shot.src;
      img.alt = shot.caption || "تصویر صفحهٔ حسابیار";
      img.loading = "lazy";
      img.width = 260;
      fig.appendChild(img);
      if (shot.caption) {
        var cap = document.createElement("figcaption");
        cap.textContent = shot.caption;
        fig.appendChild(cap);
      }
      fig.style.margin = "0";
      li.appendChild(fig);
      list.appendChild(li);
    });
    document.getElementById("screenshots").hidden = false;
  } else {
    document.querySelectorAll("[data-requires-shots]").forEach(function (el) {
      el.parentElement.hidden = true;
    });
  }

  // Latest release: direct APK links. Falls back to the static
  // /releases/latest links already in the HTML when the API is unreachable.
  var ABIS = [
    { key: "universal", label: "همه‌کاره (universal)", hint: "برای همهٔ گوشی‌ها" },
    { key: "arm-v8a", label: "arm64-v8a", hint: "بیشتر گوشی‌های جدید" },
    { key: "arm-v7a", label: "armeabi-v7a", hint: "گوشی‌های قدیمی‌تر ۳۲ بیتی" },
    { key: "x86_64", label: "x86_64", hint: "شبیه‌ساز و تبلت‌های اینتل" }
  ];

  fetch("https://api.github.com/repos/" + REPO + "/releases/latest", {
    headers: { Accept: "application/vnd.github+json" }
  })
    .then(function (res) {
      if (!res.ok) {
        throw new Error("HTTP " + res.status);
      }
      return res.json();
    })
    .then(function (release) {
      var assets = release.assets || [];
      var found = ABIS.map(function (abi) {
        var asset = assets.find(function (a) {
          return a.name.endsWith("-" + abi.key + ".apk");
        });
        return asset ? { abi: abi, asset: asset } : null;
      }).filter(Boolean);
      if (!found.length) {
        return;
      }

      var ul = document.getElementById("downloads");
      ul.textContent = "";
      found.forEach(function (item) {
        var li = document.createElement("li");
        var a = document.createElement("a");
        a.className = "dl";
        a.href = item.asset.browser_download_url;
        var strong = document.createElement("strong");
        strong.textContent = item.abi.label;
        var span = document.createElement("span");
        span.textContent = item.abi.hint + " · " + formatSize(item.asset.size);
        a.appendChild(strong);
        a.appendChild(span);
        li.appendChild(a);
        ul.appendChild(li);
      });

      var universal = found.find(function (item) {
        return item.abi.key === "universal";
      });
      if (universal) {
        document.getElementById("hero-download").href =
          universal.asset.browser_download_url;
      }
      document.getElementById("release-version").textContent = release.tag_name;
    })
    .catch(function () {
      // Keep the static fallback links.
    });
})();
