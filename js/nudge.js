/*
  Gentle (slightly pushy) reminders to call or text.

  - Once per visit, after about 25 seconds on a page or once the visitor is past the middle
    of it, a small card slides up: "Got a photo of the job? Text it to us..." with Text and
    Call buttons. It closes with × or Esc and doesn't come back until the next visit. It
    waits while the cookie banner is up, and never shows on the privacy page.
  - Every 20 seconds or so the Call buttons (in the header, and the phone call bar) give a
    small wiggle, up to three times per page, never while the visitor's pointer is on them.

  People who've asked for less motion still get the card, without it sliding, and the
  buttons never wiggle. Without this file the site has plenty of call and text links anyway.
*/
(function () {
  "use strict";

  var gsap = window.gsap;
  var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var SEEN_KEY = "eande-nudged";

  // ---------- The card ----------
  function seen() {
    try {
      return window.sessionStorage.getItem(SEEN_KEY) === "1";
    } catch (e) {
      return false;
    }
  }

  function markSeen() {
    try {
      window.sessionStorage.setItem(SEEN_KEY, "1");
    } catch (e) {
      // Private windows may refuse: then it can show once per page instead.
    }
  }

  var card = null;
  var shown = false;

  function closeCard() {
    if (!card) {
      return;
    }
    var gone = card;
    card = null;
    document.removeEventListener("keydown", onKey);
    if (gsap && !calm) {
      gsap.to(gone, { y: 20, opacity: 0, duration: 0.2, ease: "power2.in", onComplete: function () { gone.remove(); } });
    } else {
      gone.remove();
    }
  }

  function onKey(event) {
    if (event.key === "Escape") {
      closeCard();
    }
  }

  function showCard() {
    if (shown || seen()) {
      return;
    }
    // Wait for the cookie banner to be answered first.
    if (document.querySelector(".consent")) {
      window.setTimeout(showCard, 3000);
      return;
    }
    shown = true;
    markSeen();
    card = document.createElement("aside");
    card.className = "nudge-card";
    card.setAttribute("aria-label", "Free estimate");
    card.innerHTML =
      '<button class="nudge-close" type="button" aria-label="Close">×</button>' +
      '<p class="nudge-card-text"><strong>Got a photo of the job?</strong> Text it to us and ' +
      "we'll text back a ballpark estimate. Free.</p>" +
      '<div class="nudge-card-actions">' +
      '<a class="button" href="sms:+17208381503">Text a photo</a>' +
      '<a class="button button-ghost" href="tel:+17208381503">Call</a>' +
      "</div>";
    card.querySelector(".nudge-close").addEventListener("click", closeCard);
    document.addEventListener("keydown", onKey);
    document.body.appendChild(card);
    if (gsap && !calm) {
      gsap.from(card, { y: 40, opacity: 0, duration: 0.6, ease: "expo.out", clearProps: "transform,opacity" });
    }
  }

  if (!document.querySelector(".prose") && !seen()) {
    var timer = window.setTimeout(showCard, 25000);
    var onScroll = function () {
      var scrolled = window.scrollY + window.innerHeight;
      if (scrolled > document.documentElement.scrollHeight * 0.55) {
        window.removeEventListener("scroll", onScroll);
        window.clearTimeout(timer);
        showCard();
      }
    };
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  // ---------- The wiggle ----------
  if (!gsap || calm) {
    return;
  }
  var callButtons = document.querySelectorAll(".header-inner > .button-small, .call-bar .button:not(.button-ghost)");
  var wiggles = 0;

  function wiggle() {
    if (wiggles >= 3) {
      return;
    }
    if (document.visibilityState === "visible") {
      wiggles += 1;
      callButtons.forEach(function (button) {
        if (button.matches(":hover")) {
          return;
        }
        gsap.timeline({ onComplete: function () { gsap.set(button, { clearProps: "transform,transition" }); } })
          .set(button, { transition: "none" })
          .to(button, { rotation: -5, scale: 1.07, duration: 0.09, ease: "power1.out" })
          .to(button, { rotation: 5, duration: 0.1, ease: "sine.inOut" })
          .to(button, { rotation: -3, duration: 0.1, ease: "sine.inOut" })
          .to(button, { rotation: 0, scale: 1, duration: 0.3, ease: "power3.out" });
      });
    }
    window.setTimeout(wiggle, 20000);
  }

  window.setTimeout(wiggle, 12000);
})();
