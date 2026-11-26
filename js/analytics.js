/*
  Google Analytics, behind a cookie banner.

  To switch Google on, paste the site's Google Analytics 4 Measurement ID below (it looks like
  "G-XXXXXXXXXX": analytics.google.com > Admin > Data streams > the web stream). Until then the
  banner still shows and remembers each visitor's choice, but nothing loads and nothing is
  sent anywhere; visitors who accepted start being counted as soon as the ID is added.

  - A visitor who hasn't chosen yet sees a small banner: Accept or Decline.
  - Nothing goes to Google until they press Accept. Only then does Google's tag load, with
    consent for analytics only (never ads or ad personalisation), and it counts their visits.
  - Decline (or ignoring the banner) means Google's tag never loads at all.
  - The choice is remembered in this browser. "Cookie settings" in the footer brings the
    banner back to change it; changing Accept to Decline also deletes Google's cookies.

  The privacy page (privacy.html) says what's collected. Keep the two in step.
*/
(function () {
  "use strict";

  var GA_MEASUREMENT_ID = "";

  var STORE_KEY = "eande-consent";
  var id = GA_MEASUREMENT_ID.trim();
  // Without a valid ID the banner works the same, but Google's tag never loads.
  var hasId = /^G-[A-Z0-9]{4,}$/.test(id);

  // ---------- Remembering the choice ----------
  // Some browsers (private windows, blocked site data) refuse storage. Then the banner just
  // asks again next visit.
  function readChoice() {
    try {
      return window.localStorage.getItem(STORE_KEY);
    } catch (e) {
      return null;
    }
  }

  function saveChoice(choice) {
    try {
      window.localStorage.setItem(STORE_KEY, choice);
    } catch (e) {
      // Nothing to do: the choice still holds for this page.
    }
  }

  // ---------- Google's tag ----------
  window.dataLayer = window.dataLayer || [];
  function gtag() {
    window.dataLayer.push(arguments);
  }

  var loaded = false;
  function startAnalytics() {
    if (!hasId) {
      return;
    }
    if (loaded) {
      gtag("consent", "update", { analytics_storage: "granted" });
      return;
    }
    loaded = true;
    // Consent mode: analytics allowed, everything to do with ads refused.
    gtag("consent", "default", {
      analytics_storage: "granted",
      ad_storage: "denied",
      ad_user_data: "denied",
      ad_personalization: "denied"
    });
    gtag("js", new Date());
    // Google Analytics 4 never stores full IP addresses, so there is no IP setting to add.
    gtag("config", id);
    var tag = document.createElement("script");
    tag.async = true;
    tag.src = "https://www.googletagmanager.com/gtag/js?id=" + encodeURIComponent(id);
    document.head.appendChild(tag);
  }

  /** Stops Google counting and deletes its cookies (_ga, _ga_XXXX) from this site. */
  function stopAnalytics() {
    if (loaded) {
      gtag("consent", "update", { analytics_storage: "denied" });
    }
    var host = window.location.hostname;
    var domains = ["", host, "." + host, "." + host.split(".").slice(-2).join(".")];
    document.cookie.split(";").forEach(function (cookie) {
      var name = cookie.split("=")[0].trim();
      if (name === "_ga" || name.indexOf("_ga_") === 0 || name === "_gid") {
        domains.forEach(function (domain) {
          document.cookie = name + "=; Max-Age=0; path=/" + (domain ? "; domain=" + domain : "");
        });
      }
    });
  }

  // ---------- The banner ----------
  var banner = null;

  function hideBanner() {
    if (!banner) {
      return;
    }
    var gone = banner;
    banner = null;
    var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (window.gsap && !calm) {
      window.gsap.to(gone, {
        y: 24, opacity: 0, duration: 0.2, ease: "power2.in",
        onComplete: function () { gone.remove(); }
      });
    } else {
      gone.remove();
    }
  }

  function choose(choice) {
    saveChoice(choice);
    if (choice === "granted") {
      startAnalytics();
    } else {
      stopAnalytics();
    }
    hideBanner();
  }

  function showBanner() {
    if (banner) {
      return;
    }
    banner = document.createElement("section");
    banner.className = "consent";
    banner.setAttribute("aria-label", "Cookies");
    banner.innerHTML =
      '<p class="consent-text">We use Google Analytics cookies to see how people find and use this site, ' +
      'so we can make it better. Nothing is collected unless you accept. ' +
      '<a href="privacy">Privacy</a></p>' +
      '<div class="consent-actions">' +
      '<button class="button" type="button" data-choice="granted">Accept</button>' +
      '<button class="button button-ghost" type="button" data-choice="denied">Decline</button>' +
      "</div>";
    banner.addEventListener("click", function (event) {
      var button = event.target.closest("[data-choice]");
      if (button) {
        choose(button.getAttribute("data-choice"));
      }
    });
    document.body.appendChild(banner);

    var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (window.gsap && !calm) {
      window.gsap.from(banner, { y: 32, opacity: 0, duration: 0.5, ease: "expo.out", delay: 0.6, clearProps: "transform,opacity" });
    }
  }

  // ---------- Start ----------
  var choice = readChoice();
  if (choice === "granted") {
    startAnalytics();
  } else if (choice !== "denied") {
    showBanner();
  }

  // The footer's "Cookie settings" button, hidden on pages until this script runs.
  document.querySelectorAll(".cookie-settings").forEach(function (button) {
    button.hidden = false;
    button.addEventListener("click", showBanner);
  });
})();
