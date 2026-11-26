# E&E checklist

## Before launch: real details from the owner
Search the `.html` files for `PLACEHOLDER:` (`grep -l "PLACEHOLDER:" *.html`; the colon skips the notes at the top of each page that only mention the word). The Docker build won't run until they're all gone.
The questions to ask him are in EandE-finish-checklist.md.
- [x] Phone number: (720) 838-1503, takes texts
- [x] Hours: Mon–Fri, 7am–5pm Mountain Time, emergency calls anytime
- [ ] Email address: taken off the site for now, so it launches with call and text only. When the owner's Gmail exists, add it back to the contact cards and footer on every page and as "email" in the structured data in index.html (the hours card stops spanning the row by itself)
- [x] No license number on the site (owner's call). The footer says "Licensed & insured" instead
- [x] "Licensed and insured" is true (FAQ, footer)
- [ ] Have him read the five service pages (electrical, maintenance, trees, concrete, remodels). The "What we handle" descriptions are drafts
- [x] Remodels page no longer mentions electrical work
- [ ] Confirm the mountain towns. The El Paso County and Denver-side (I-25) town lists are confirmed. The service area is Colorado Springs, El Paso County and the mountain towns to Aspen, but the 10 mountain towns listed (Cascade to Aspen) were picked by us. The owner said they serve all the way to Denver; the 11 I-25 towns (Castle Rock to Denver) were also picked by us. Each has its own page, so adding or dropping one means a page, `Server.TOWNS`, sitemap.xml and the areas page
- [ ] Go through the FAQ answers with him (faq.html). Confirmed so far: licensed and insured, free estimates with the $150 repair diagnostic, a ballpark estimate from a texted photo, urgent calls (with an extra fee after hours), hours, landlords and property managers, permits, cleanup. Still open: the mountain towns in the service-area answer. Keep the structured data at the top of faq.html matching; a test checks
- [x] Logo: at the top of the hero on every page (logo.webp, from art/logo-original.png)
- [x] Business name matches the logo exactly: "E&E Property Maintenance & Home Improvement, LLC" (titles, header, structured data, footer, share image). Use the same on the Google Business Profile
- [ ] No clean logo file is coming. The one he sent has a fake checkerboard painted in, so logo.webp only works on dark backgrounds. If it's ever needed on a light background (print, listings), cut it out ourselves or redraw it as an SVG
- [ ] Real job photos: none yet. Add them during monthly maintenance as they come in

## Maps
- [ ] Service area outline on the areas page: import art/service-area.kml into Google My Maps (mymaps.google.com), fix the shape (it predates the Denver extension, so stretch it up I-25 to Denver), make it public, and put its `https://www.google.com/maps/d/embed?mid=...` address in areas.html (marked PLACEHOLDER). Best done in the business's own Google account
- [ ] Open a few town pages and check Google outlines the town. Small unincorporated places (Twin Lakes, Divide, Hartsel, Cascade) may only show a pin
- [ ] Optional: get a Google Maps Embed API key and switch the map frames to the official Embed API. The free embed they use now works without a key, but Google could change it

## Payment
- [ ] Client has paid. **Don't deploy before this.**

## Launch
- [ ] `fly launch --no-deploy` then `fly deploy` (Fly billing must be sorted first, see the portfolio notes)
- [ ] Domain: the name is picked; buy it after he pays, then add it to `ALLOWED_HOSTS` in fly.toml, `fly certs add <domain>`
- [ ] Swap `eande.fly.dev` for the real domain in every `.html` file (canonical, og:url, og:image, structured data), robots.txt, sitemap.xml and the tests
- [ ] Before the real domain goes live: every subdomain must work on HTTPS, because Strict-Transport-Security now has `includeSubDomains`
- [x] Share preview image (og-image.png, source in art/) and phone home-screen icon
- [x] Text is squashed with Brotli at build time (the Dockerfile installs it; locally, `brew install brotli`)

## Getting on Google (after launch)
Do these in order, once the site is live on its real domain. Use the business's Google account (the owner's new Gmail), not a personal one, so he keeps it if you stop working on the site.

1. **Check the site is ready.** Open https://<domain>/robots.txt and https://<domain>/sitemap.xml in a browser. Both should load and show the real domain, not eande.fly.dev.
2. **Google Search Console** (search.google.com/search-console), signed in as the business account:
   - Add property, choose **Domain**, type the bare domain (example.com).
   - Google shows a TXT record. Add it at the domain registrar under DNS, then press Verify (it can take up to an hour to show up).
   - Sitemaps (left menu): enter `sitemap.xml` and submit. It lists all 36 pages.
   - URL Inspection (top bar): paste the home page address, then Request indexing. Do the same for /electrical, /areas and /colorado-springs. The rest get found through the sitemap.
   - Pages usually start appearing in a few days to two weeks. Check Pages (left menu) after a week for anything "not indexed" and why.
3. **Google Business Profile** (business.google.com). This matters most for showing up in Maps and "electrician near me". He doesn't have one yet (confirmed), so create a new one in the business Gmail:
   - Same exact business name, phone (720) 838-1503 and hours as the site. Category: Electrician, then add Handyman, Tree service, Concrete contractor and Remodeler.
   - He has no separate business address (confirmed), so set it up as a **service-area business**: give his home address for verification, choose not to show it, and list the service area (El Paso County, the I-25 towns to Denver, the mountain towns to Aspen).
   - Website: the home page. Verify by the postcard, phone or video Google offers.
   - Add real job photos and ask a few happy customers for reviews.
4. **Bing Webmaster Tools** (bing.com/webmasters): sign in and choose Import from Google Search Console. It brings the site and sitemap across in a minute.
5. **Check the extras:** run the home page and /faq through search.google.com/test/rich-results (the business details and FAQ should show with no errors), and paste the link into a text to check the share picture shows.
- [ ] Run the home page and faq page through Google's Rich Results Test to check the structured data
- [ ] Paste the link into a text to check the share preview shows the image
- [ ] Monthly maintenance (in the price): set up and keep Yelp, Nextdoor, Angi, BBB and Facebook listings, with the exact name "E&E Property Maintenance & Home Improvement, LLC" and (720) 838-1503, no street address (service-area business). Consistent listings help local ranking
- [ ] Google reviews: the owner has started collecting them
- [ ] Grow the web presence (listings, photos, reviews, posts) month by month under the maintenance plan
- [ ] Bump `<lastmod>` in sitemap.xml when a page changes a lot

## Google Analytics and the cookie banner
Built and tested, switched off until there's a Measurement ID. Nothing goes to Google until a visitor presses Accept; Decline (or ignoring the banner) means Google's tag never loads. The privacy page (/privacy) explains it, and "Cookie settings" in every footer brings the banner back.
- [ ] Create the property at analytics.google.com, signed in as the business Gmail (not a personal account): Admin > Create > Property, name "E&E Property Maintenance & Home Improvement, LLC", time zone Mountain (Denver), currency USD
- [ ] Add a **Web** data stream for the real domain once it's bought. Copy its Measurement ID (G-XXXXXXXXXX)
- [ ] Paste it into `js/analytics.js`: `var GA_MEASUREMENT_ID = "G-XXXXXXXXXX";`, run `./run.sh test`, deploy
- [ ] Check Google receives it: open the site, press Accept, then in Analytics go to Reports > Realtime. Your visit shows within a minute. (A visitor who declines never shows, by design)
- [ ] Optional: Admin > Data retention, set to 14 months; Admin > Data collection, leave Google signals off (it's for ads)
- [ ] Link Analytics to Search Console (Admin > Product links) so search terms show up in Analytics
- [ ] If the ID goes on the GitHub Pages test copy too, those preview visits will be counted. Better to add it only for the real site

## Visitor counts (optional)
- [ ] Cloudflare Web Analytics: free, no cookies, so no cookie banner. At dash.cloudflare.com > Analytics & Logs > Web Analytics, add the site's domain (choose the manual setup, the site doesn't need Cloudflare DNS). Copy the token from the snippet it shows (the part after `"token": "`), paste it as `ANALYTICS_TOKEN` in fly.toml, then `fly deploy`. Visits show in that dashboard within a few minutes. Locally: `ANALYTICS_TOKEN=... ./run.sh`

## Git
- [x] First commit, pushed to GitHub (codingsqurl/EandE)
- [ ] The repo is **public**. Decide whether to make it private. Nothing secret is in it, but the owner's details will be. GitHub Pages (`./run.sh pages`) needs a public repo on a free plan
- [ ] Merge `portfolio-style` into `master` once he's happy with it

## Security, ongoing
- [ ] Rebuild and redeploy every month or two so the Java base image picks up security fixes
- [x] `includeSubDomains` added to Strict-Transport-Security
- [ ] Optional: add /.well-known/security.txt with a contact email, once there's a real one
