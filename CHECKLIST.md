# E&E checklist

## Before launch: real details from the owner
Search the `.html` files for `PLACEHOLDER` (`grep -l PLACEHOLDER *.html`). The Docker build won't run until they're all gone.
The questions to ask him are in EandE-finish-checklist.md.
- [x] Phone number: (720) 838-1503, takes texts
- [x] Hours: Mon–Fri, 7am–5pm Mountain Time, emergency calls anytime
- [ ] Email address (contact section and footer on every page, and the structured data in index.html)
- [ ] Colorado electrical license number (footer on every page)
- [ ] Confirm "licensed and insured" is true (FAQ)
- [ ] Have him read the five service pages (electrical, maintenance, trees, concrete, remodels). The "What we handle" descriptions are drafts
- [x] Remodels page no longer mentions electrical work
- [ ] Confirm the towns. The service area is Colorado Springs, El Paso County and the mountain towns to Aspen, but the 10 mountain towns listed (Cascade to Aspen) were picked by us. Each has its own page, so adding or dropping one means a page, `Server.TOWNS`, sitemap.xml and the areas page
- [ ] Go through the FAQ answers with him (faq.html). Confirmed so far: free estimates with the $150 repair diagnostic, urgent calls, hours. Still drafts: licensed/insured, permits, landlords, service area, cleanup. Keep the structured data at the top of faq.html matching; a test checks
- [x] Logo: at the top of the hero on every page (logo.webp, from art/logo-original.png)
- [ ] Business name: the logo says "E&E Property Maintenance & Home Improvement, LLC", the site says "E&E Electrical & Property Maintenance". Pick one and use it everywhere (titles, structured data, footer, Google Business Profile)
- [ ] Ask for the logo file with real transparency (or as an SVG). The one he sent has a fake checkerboard painted in, so the cleaned-up copy only works on dark backgrounds
- [ ] Real job photos, if he has them

## Maps
- [ ] Service area outline on the areas page: import art/service-area.kml into Google My Maps (mymaps.google.com), fix the shape, make it public, and put its `https://www.google.com/maps/d/embed?mid=...` address in areas.html (marked PLACEHOLDER). Best done in the business's own Google account
- [ ] Open a few town pages and check Google outlines the town. Small unincorporated places (Twin Lakes, Divide, Hartsel, Cascade) may only show a pin
- [ ] Optional: get a Google Maps Embed API key and switch the map frames to the official Embed API. The free embed they use now works without a key, but Google could change it

## Payment
- [ ] Client has paid. **Don't deploy before this.**

## Launch
- [ ] `fly launch --no-deploy` then `fly deploy` (Fly billing must be sorted first, see the portfolio notes)
- [ ] Domain: buy or connect it, add it to `ALLOWED_HOSTS` in fly.toml, `fly certs add <domain>`
- [ ] Swap `eande.fly.dev` for the real domain in every `.html` file (canonical, og:url, og:image, structured data), robots.txt, sitemap.xml and the tests
- [ ] Before the real domain goes live: every subdomain must work on HTTPS, because Strict-Transport-Security now has `includeSubDomains`
- [x] Share preview image (og-image.png, source in art/) and phone home-screen icon
- [x] Text is squashed with Brotli at build time (the Dockerfile installs it; locally, `brew install brotli`)

## SEO after launch
- [ ] Google Business Profile: same name, phone and hours as the site, link to the site. This matters most for local search
- [ ] Google Search Console: verify the domain, submit sitemap.xml (it lists all 25 pages), request indexing
- [ ] Bing Webmaster Tools: import from Search Console
- [ ] Run the home page and faq page through Google's Rich Results Test to check the structured data
- [ ] Paste the link into a text to check the share preview shows the image
- [ ] Same name, phone and address on Yelp, Nextdoor, Angi, BBB (consistent listings help local ranking)
- [ ] Ask happy customers for Google reviews
- [ ] Bump `<lastmod>` in sitemap.xml when a page changes a lot

## Git
- [x] First commit, pushed to GitHub (codingsqurl/EandE)
- [ ] The repo is **public**. Decide whether to make it private. Nothing secret is in it, but the owner's details will be. GitHub Pages (`./run.sh pages`) needs a public repo on a free plan
- [ ] Merge `portfolio-style` into `master` once he's happy with it

## Security, ongoing
- [ ] Rebuild and redeploy every month or two so the Java base image picks up security fixes
- [x] `includeSubDomains` added to Strict-Transport-Security
- [ ] Optional: add /.well-known/security.txt with a contact email, once there's a real one
