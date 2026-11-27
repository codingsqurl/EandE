#!/bin/sh
# Builds and runs the Java server on this computer.
#
#   ./run.sh           start the server at http://127.0.0.1:8001 (the portfolio uses 8000)
#   ./run.sh test      run the tests
#   ./run.sh pages     put a test copy on GitHub Pages, for checking on a phone (public, hidden from Google)
#
# It needs Java 21: brew install openjdk@21
set -e
cd "$(dirname "$0")"

# Use Homebrew's Java if it isn't already on the PATH.
if [ -x /opt/homebrew/opt/openjdk@21/bin/javac ]; then
  PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
fi

if [ "$1" = "test" ]; then
  # The tests sit beside the server code but never go in the container.
  rm -rf build-test
  javac -d build-test --release 21 $(find server/src server/test -name '*.java')
  exec java -Deande.quiet=true -cp build-test eande.AllTests
fi

if [ "$1" = "pages" ]; then
  # GitHub Pages serves plain files, without the Java server's firewall or security headers,
  # so this is only a test copy. The noindex tag keeps Google from listing it next to the real site.
  rm -rf build-pages
  mkdir build-pages
  cp style.css favicon.svg logo.webp apple-touch-icon.png og-image.png build-pages/
  # Only the scripts the pages load, never anything else that lands in js/.
  mkdir build-pages/js
  cp js/gsap-3.12.5.min.js js/ScrollTrigger-3.12.5.min.js js/motion.js js/analytics.js js/nudge.js build-pages/js/
  # Like the Java server, add the stylesheet's fingerprint to its link, so phones fetch the new
  # styles after every publish instead of reusing a saved copy. GitHub Pages serves
  # electrical.html at /electrical, so the links between pages work there too.
  version="$(shasum style.css | cut -c1-8)"
  motion="$(shasum js/motion.js | cut -c1-8)"
  analytics="$(shasum js/analytics.js | cut -c1-8)"
  nudge="$(shasum js/nudge.js | cut -c1-8)"
  for page in *.html; do
    awk '{ print } /<meta charset="UTF-8">/ { print "    <meta name=\"robots\" content=\"noindex\">" }' \
      "$page" | sed -e "s|href=\"style.css\"|href=\"style.css?v=$version\"|" \
        -e "s|src=\"js/motion.js\"|src=\"js/motion.js?v=$motion\"|" \
        -e "s|src=\"js/analytics.js\"|src=\"js/analytics.js?v=$analytics\"|" \
        -e "s|src=\"js/nudge.js\"|src=\"js/nudge.js?v=$nudge\"|" > "build-pages/$page"
  done
  touch build-pages/.nojekyll
  # The gh-pages branch holds only the latest copy, so it's replaced each time.
  origin="$(git remote get-url origin)"
  cd build-pages
  git init -q -b gh-pages
  git add -A
  git commit -q -m "Test copy for GitHub Pages"
  exec git push -q -f "$origin" gh-pages
fi

# Compile every .java file into build/. It takes a second or two.
rm -rf build
javac -d build --release 21 $(find server/src -name '*.java')

# Squash the site's text with Brotli ahead of time (brew install brotli). Without it,
# or for a file edited while the server runs, the server falls back to gzip.
rm -rf br
if command -v brotli > /dev/null; then
  java -cp build eande.Server brotli-sources
  brotli -q 11 --rm br/*
fi

HOST="${HOST:-127.0.0.1}" PORT="${PORT:-8001}" exec java -cp build eande.Server
