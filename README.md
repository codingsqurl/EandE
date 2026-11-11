# E&E Electrical & Property Maintenance

Website for E&E, an electrical and property maintenance company in Colorado Springs.

- **Site:** plain HTML and CSS (`index.html`, `style.css`, `404.html`). No JavaScript.
- **Server:** Java 21 using only the JDK (`server/src/eande/`). It's a trimmed copy of the
  portfolio's server: the same firewall, security headers, caching and gzip.
- **Hosting:** Docker on Fly.io (app `eande`, Denver region).

## Run it

    brew install openjdk@21   # once
    ./run.sh                  # http://127.0.0.1:8001 (the portfolio uses 8000)
    ./run.sh test             # the server and firewall tests

## Deploy (only after the client pays, see CHECKLIST.md)

    fly launch --no-deploy    # first time only: creates the app from fly.toml
    fly deploy

The Docker build refuses to run while `index.html` still has `PLACEHOLDER` details.
