/*
  The site's motion, run by GSAP (js/gsap-3.12.5.min.js and js/ScrollTrigger-3.12.5.min.js).

  The page is complete without this file: every part is visible in the HTML and CSS alone,
  so if a script fails to load, nothing stays hidden. What it adds:

  - Home page: the logo behind the hero lights up and the five service tiles arrive one by
    one. As each lands, its icon does its trade: the bolt zaps, the wrench turns, the tree
    sways, the concrete settles, the house hops.
  - Everywhere: hovering or tabbing to a service (in the tiles or the menu) plays its icon
    again. The menu's items drop in when it opens. FAQ answers slide open and shut.
    Groups of cards and towns rise into place as they scroll into view.
  - Phones: the Call / Text bar slides up once the top of the page has scrolled away.

  People who've asked their device for less motion get none of this: things simply appear
  and change state at once, the icons hold still and the call bar stays put. Headings and paragraphs are never animated
  letter by letter; text at most fades in with its section.
*/
(function () {
  "use strict";

  var root = document.documentElement;
  if (!window.gsap) {
    // GSAP didn't load. The CSS shows everything on its own after a moment.
    return;
  }
  var gsap = window.gsap;
  if (window.ScrollTrigger) {
    gsap.registerPlugin(window.ScrollTrigger);
  }

  var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var arrive = "expo.out";

  // ---------- Each service's icon gesture ----------
  // One short, one-off move per trade. Each returns a paused timeline, played on landing,
  // hover and keyboard focus. A gesture already playing finishes before it can start again.
  var gestures = {
    electrical: function (icon) {
      // The bolt strikes from the ground up: it's drawn in from its bottom tip to the top,
      // speeding up as it climbs, then flashes once and settles. immediateRender: false keeps
      // the bolt whole until the strike actually plays.
      return gsap.timeline({ paused: true })
        .fromTo(icon,
          { clipPath: "inset(100% 0% 0% 0%)" },
          { clipPath: "inset(0% 0% 0% 0%)", duration: 0.24, ease: "power2.in", immediateRender: false })
        .to(icon, { scale: 1.22, duration: 0.07, ease: "power2.out" })
        .to(icon, { opacity: 0.55, duration: 0.05, ease: "none", yoyo: true, repeat: 1 }, "<")
        .to(icon, { scale: 1, duration: 0.3, ease: "power3.out" })
        .set(icon, { clearProps: "clipPath" });
    },
    maintenance: function (icon) {
      // Turns around the wrench's head, like tightening a bolt.
      gsap.set(icon, { transformOrigin: "62% 38%" });
      return gsap.timeline({ paused: true })
        .to(icon, { rotation: -42, duration: 0.25, ease: "power2.inOut" })
        .to(icon, { rotation: 12, duration: 0.2, ease: "power2.inOut" })
        .to(icon, { rotation: 0, duration: 0.35, ease: "power3.out" });
    },
    trees: function (icon) {
      // A little tree removal: the branches are stripped from the bottom up, the top is
      // lowered down the trunk, the bare pole timbers over, and a fresh tree grows back.
      // (The icon is drawn in those parts in the HTML, and looks whole without this.)
      var lowLeft = icon.querySelector(".tree-low.tree-left");
      var lowRight = icon.querySelector(".tree-low.tree-right");
      var highLeft = icon.querySelector(".tree-high.tree-left");
      var highRight = icon.querySelector(".tree-high.tree-right");
      var top = icon.querySelector(".tree-top");
      var pole = icon.querySelector(".tree-pole");
      var removal = gsap.timeline({ paused: true });
      if (!lowLeft || !lowRight || !highLeft || !highRight || !top || !pole) {
        return removal;
      }
      var parts = [lowLeft, lowRight, highLeft, highRight, top, pole];

      /** One side of a layer of branches, cut and dropping away from the trunk. */
      function strip(branch, side, at) {
        removal.to(branch, {
          x: 3.5 * side, y: 4, rotation: 35 * side, opacity: 0,
          svgOrigin: "12 " + (branch.classList.contains("tree-low") ? "17" : "11"),
          duration: 0.3, ease: "power2.in"
        }, at);
      }

      strip(lowLeft, -1, 0);
      strip(lowRight, 1, 0.06);
      strip(highLeft, -1, 0.28);
      strip(highRight, 1, 0.34);
      removal
        // The top is lowered down the pole, like it's on a rope, and set on the ground.
        .to(top, { y: 13, duration: 0.42, ease: "sine.inOut" }, 0.6)
        .to(top, { opacity: 0, duration: 0.12 }, 0.96)
        // Timber! The bare pole falls over from its base and lands.
        .to(pole, { rotation: 88, svgOrigin: "12 22", duration: 0.36, ease: "power2.in" }, 1.04)
        .to(pole, { rotation: 82, svgOrigin: "12 22", duration: 0.08, ease: "power1.out" }, 1.4)
        .to(pole, { opacity: 0, duration: 0.14 }, 1.44)
        // A new tree grows back from the ground.
        .set(parts, { clearProps: "transform,opacity" }, 1.58)
        .fromTo(icon,
          { scale: 0, transformOrigin: "50% 100%" },
          { scale: 1, duration: 0.38, ease: "back.out(1.6)", immediateRender: false }, 1.58);
      return removal;
    },
    concrete: function (icon) {
      // The wall goes up: the footing pours in from the left, then each block drops onto it
      // one by one, the bottom row left to right, then the top row, landing with a little
      // squash. (The icon is drawn as the footing and five blocks in the HTML, bottom row
      // first, and looks whole without this.)
      var footing = icon.querySelector(".wall-footing");
      var bricks = gsap.utils.toArray(icon.querySelectorAll(".wall-brick"));
      var wall = gsap.timeline({ paused: true });
      if (!footing || bricks.length !== 5) {
        return wall;
      }
      // Everything starts out of place. immediateRender: false keeps the wall whole until
      // the build actually plays.
      wall
        .set(footing, { scaleX: 0, svgOrigin: "2 21", immediateRender: false }, 0)
        .set(bricks, { y: -7, opacity: 0, immediateRender: false }, 0)
        .to(footing, { scaleX: 1, svgOrigin: "2 21", duration: 0.3, ease: "power3.out" }, 0);
      bricks.forEach(function (brick, index) {
        var at = 0.24 + index * 0.13;
        wall
          .to(brick, { opacity: 1, duration: 0.08 }, at)
          .to(brick, { y: 0, duration: 0.2, ease: "power2.in" }, at)
          .to(brick, { scaleY: 0.8, scaleX: 1.06, transformOrigin: "50% 100%", duration: 0.05, ease: "power1.out" }, at + 0.2)
          .to(brick, { scaleY: 1, scaleX: 1, duration: 0.16, ease: "power2.out" }, at + 0.25);
      });
      return wall.set(bricks.concat(footing), { clearProps: "transform,opacity" });
    },
    remodels: function (icon) {
      // The house goes up piece by piece: the two walls rise from the ground, the beam over
      // the door drops in, then the roof lands on top and the whole house settles.
      // (The icon is drawn in those four parts in the HTML, and looks whole without this.)
      var walls = icon.querySelectorAll(".house-wall");
      var lintel = icon.querySelector(".house-lintel");
      var roof = icon.querySelector(".house-roof");
      gsap.set(icon, { transformOrigin: "50% 100%" });
      var build = gsap.timeline({ paused: true });
      if (walls.length !== 2 || !lintel || !roof) {
        return build;
      }
      // Each piece starts out of place. immediateRender: false keeps the house whole until
      // the build actually plays.
      build
        .set(walls, { scaleY: 0, transformOrigin: "50% 100%", immediateRender: false }, 0)
        .set(lintel, { y: -5, opacity: 0, immediateRender: false }, 0)
        .set(roof, { y: -9, opacity: 0, immediateRender: false }, 0)
        // About 1.7 seconds in all, slow enough to watch each piece go on.
        .to(walls, { scaleY: 1, duration: 0.45, ease: "power3.out", stagger: 0.18 }, 0)
        .to(lintel, { y: 0, opacity: 1, duration: 0.3, ease: "power2.out" }, 0.5)
        .to(roof, { opacity: 1, duration: 0.12 }, 0.78)
        .to(roof, { y: 0, duration: 0.4, ease: "power2.in" }, 0.78)
        .to(icon, { scaleY: 0.88, scaleX: 1.07, duration: 0.08, ease: "power1.out" }, 1.18)
        .to(icon, { scaleY: 1, scaleX: 1, duration: 0.45, ease: "power3.out" }, 1.26);
      return build;
    }
  };

  /** The service a link points to ("electrical", "trees"...), from its address. */
  function serviceOf(link) {
    var href = (link.getAttribute("href") || "").replace(/^\.?\//, "");
    return gestures[href] ? href : null;
  }

  /** Gives a link's icon its gesture, played on hover and focus. Returns the timeline. */
  function wireGesture(link, icon) {
    var service = serviceOf(link);
    if (!service || !icon) {
      return null;
    }
    var gesture = gestures[service](icon);
    function play() {
      if (!gesture.isActive()) {
        gesture.restart();
      }
    }
    link.addEventListener("pointerenter", function (event) {
      if (event.pointerType === "mouse") {
        play();
      }
    });
    link.addEventListener("focus", play);
    return gesture;
  }

  var tileGestures = new Map();
  if (!calm) {
    document.querySelectorAll(".picker-option").forEach(function (tile) {
      var gesture = wireGesture(tile, tile.querySelector(".picker-icon"));
      if (gesture) {
        tileGestures.set(tile, gesture);
      }
    });
    document.querySelectorAll(".nav-service").forEach(function (link) {
      wireGesture(link, link.querySelector("svg"));
    });
  }

  // ---------- Home page: the hero lights up and the crew arrives ----------
  // Until this script adds "motion-on" to the page, the CSS holds back the home hero's logo
  // and tiles and the phone call bar (see "Motion" in style.css), and shows them anyway after
  // two seconds if it never comes. Everything below runs in one go, so the page is never
  // drawn between the CSS letting go and GSAP taking over.
  var intro = document.querySelector(".hero-intro");
  if (intro) {
    var tiles = gsap.utils.toArray(".hero-intro .picker-option");
    var late = performance.now() > 1600;
    if (calm || late) {
      // Show everything as it is. Too late to stage an entrance the visitor half saw.
      gsap.set(intro, { "--logo-o": 0.3 });
      gsap.set(tiles, { opacity: 1 });
      root.classList.add("motion-on");
    } else {
      gsap.set(intro, { "--logo-o": 0, "--logo-s": 1.06 });
      gsap.set(tiles, { opacity: 0, y: 26, scale: 0.96, transition: "none" });
      root.classList.add("motion-on");

      var show = gsap.timeline({ defaults: { ease: arrive } });
      show.to(intro, { "--logo-o": 0.3, "--logo-s": 1, duration: 0.9, ease: "power2.out" }, 0);
      tiles.forEach(function (tile, index) {
        var at = 0.15 + index * 0.09;
        show.to(tile, {
          opacity: 1, y: 0, scale: 1, duration: 0.8,
          // Hand the tile back to the CSS, so its hover lift works again.
          clearProps: "transform,transition"
        }, at);
        var gesture = tileGestures.get(tile);
        if (gesture) {
          show.add(function () { gesture.restart(); }, at + 0.32);
        }
      });
      show.from(".hero-intro .hero-call", { opacity: 0, y: 10, duration: 0.6, clearProps: "transform" }, 0.45);
    }
  } else {
    root.classList.add("motion-on");
  }

  // ---------- The menu: items drop in when it opens ----------
  // The panel itself fades and drops in through CSS (@starting-style in style.css). Tweening the
  // popover element from script makes Chrome close it again, so GSAP only moves what's inside.
  var menu = document.getElementById("site-menu");
  if (menu && !calm) {
    menu.addEventListener("toggle", function (event) {
      if (event.newState !== "open") {
        return;
      }
      gsap.fromTo(menu.querySelectorAll(".nav-heading, .nav-service, .nav-links a, .nav-contact"),
        { opacity: 0, y: -6 },
        // The whole cascade is in within about a third of a second, Call and Text included.
        { opacity: 1, y: 0, duration: 0.24, ease: arrive, stagger: { amount: 0.1 }, clearProps: "transform,opacity" });
    });
  }

  // ---------- FAQ: answers slide open and shut ----------
  // The <details> still opens and closes on its own; this only animates the answer's height.
  document.querySelectorAll("details.faq").forEach(function (item) {
    var summary = item.querySelector("summary");
    var answer = summary && summary.nextElementSibling;
    if (!answer) {
      return;
    }
    summary.addEventListener("click", function (event) {
      if (calm) {
        return;
      }
      event.preventDefault();
      gsap.killTweensOf(answer);
      var reopening = item.classList.contains("closing");
      if (!item.open || reopening) {
        // Opening, or reopening one that's still sliding shut (from wherever it has got to).
        item.classList.remove("closing");
        item.open = true;
        gsap.fromTo(answer,
          reopening ? { overflow: "hidden" } : { height: 0, opacity: 0, overflow: "hidden" },
          { height: "auto", opacity: 1, duration: 0.32, ease: "power3.out", clearProps: "height,opacity,overflow" });
      } else {
        item.classList.add("closing");
        gsap.to(answer, {
          height: 0, opacity: 0, overflow: "hidden", duration: 0.22, ease: "power2.in",
          onComplete: function () {
            item.open = false;
            item.classList.remove("closing");
            gsap.set(answer, { clearProps: "height,opacity,overflow" });
          }
        });
      }
    });
  });

  if (!window.ScrollTrigger || calm) {
    return;
  }

  // ---------- Scrolling: groups rise into place ----------
  // Only things still below the screen are hidden, so nothing already in view blinks.
  function belowScreen(element) {
    return element.getBoundingClientRect().top > window.innerHeight;
  }

  function rise(selector, options) {
    var items = gsap.utils.toArray(selector).filter(belowScreen);
    if (!items.length) {
      return;
    }
    gsap.set(items, { opacity: 0, y: options.y, transition: "none" });
    window.ScrollTrigger.batch(items, {
      start: "top 90%",
      once: true,
      onEnter: function (batch) {
        gsap.to(batch, {
          opacity: 1, y: 0, duration: options.duration, ease: arrive,
          // Keep a long row from dragging: the whole batch is in within a third of a second.
          stagger: Math.min(options.stagger, 0.35 / batch.length),
          clearProps: "transform,opacity,transition"
        });
      }
    });
  }

  // Cards, steps, questions and towns arrive as lists.
  rise(".reasons > *, .service-columns > *, .contact-grid > *, .faq-list > *", { y: 32, duration: 0.8, stagger: 0.08 });
  rise(".steps li", { y: 24, duration: 0.7, stagger: 0.12 });
  rise(".towns > *", { y: 14, duration: 0.6, stagger: 0.03 });
  // Section headings and intros just fade up a touch, once.
  rise(".section h2, .section .lead, .towns-heading", { y: 10, duration: 0.6, stagger: 0.06 });

  // The remodel steps' numbers pop in as their step arrives.
  gsap.utils.toArray(".steps li").filter(belowScreen).forEach(function (step) {
    var number = step.querySelector(".step-number");
    if (number) {
      gsap.from(number, {
        scale: 0.4, opacity: 0, duration: 0.6, ease: "back.out(2)", delay: 0.12,
        clearProps: "transform,opacity",
        scrollTrigger: { trigger: step, start: "top 90%", once: true }
      });
    }
  });

  // ---------- Phones: the call bar slides up once the hero is gone ----------
  var bar = document.querySelector(".call-bar");
  if (bar) {
    gsap.matchMedia().add("(max-width: 680px)", function () {
      var hidden = { yPercent: 110, duration: 0.3, ease: "power2.in" };
      var shown = { yPercent: 0, duration: 0.45, ease: arrive };
      gsap.set(bar, { yPercent: window.scrollY > 400 ? 0 : 110 });
      var trigger = window.ScrollTrigger.create({
        start: 400,
        onEnter: function () { gsap.to(bar, shown); },
        onLeaveBack: function () { gsap.to(bar, hidden); }
      });
      return function () {
        trigger.kill();
        gsap.set(bar, { clearProps: "transform" });
      };
    });
  }
})();
