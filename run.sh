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
  cp 404.html style.css favicon.svg apple-touch-icon.png og-image.png build-pages/
  awk '{ print } /<meta charset="UTF-8">/ { print "    <meta name=\"robots\" content=\"noindex\">" }' \
    index.html > build-pages/index.html
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
HOST="${HOST:-127.0.0.1}" PORT="${PORT:-8001}" exec java -cp build eande.Server
