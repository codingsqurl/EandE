# E&E checklist

## Before launch: real details from the owner
Search `index.html` for `PLACEHOLDER`. The Docker build won't run until they're all gone.
- [ ] Phone number (header, hero, contact, structured data)
- [ ] Email address
- [ ] Hours
- [ ] Colorado electrical license number (footer)
- [ ] Confirm "Licensed & insured" and the other hero claims are true
- [ ] Confirm the service lists (electrical, maintenance, tree work, concrete) and the remodel types
- [ ] Confirm the towns in the service area
- [ ] Go through the FAQ answers with him (licensed/insured, free estimates, permits, urgent calls). Keep the structured data matching; a test checks
- [ ] Logo, if he has one (otherwise keep the lightning-bolt mark)
- [ ] Real job photos, if he has them

## Payment
- [ ] Client has paid. **Don't deploy before this.**

## Launch
- [ ] `fly launch --no-deploy` then `fly deploy` (Fly billing must be sorted first, see the portfolio notes)
- [ ] Domain: buy or connect it, add it to `ALLOWED_HOSTS` in fly.toml, `fly certs add <domain>`
- [ ] Swap `eande.fly.dev` for the real domain in index.html (canonical, og:url, og:image, structured data), robots.txt, sitemap.xml and the tests
- [x] Share preview image (og-image.png, source in art/) and phone home-screen icon

## SEO after launch
- [ ] Google Business Profile: same name, phone and hours as the site, link to the site. This matters most for local search
- [ ] Google Search Console: verify the domain, submit sitemap.xml, request indexing
- [ ] Bing Webmaster Tools: import from Search Console
- [ ] Run the page through Google's Rich Results Test to check the structured data
- [ ] Paste the link into a text to check the share preview shows the image
- [ ] Same name, phone and address on Yelp, Nextdoor, Angi, BBB (consistent listings help local ranking)
- [ ] Ask happy customers for Google reviews
- [ ] Bump `<lastmod>` in sitemap.xml when the page changes a lot

## Git
- [ ] First commit, then push to a private GitHub repo

## Security, ongoing
- [ ] Rebuild and redeploy every month or two so the Java base image picks up security fixes
- [ ] Once the real domain is live on HTTPS everywhere, consider adding `includeSubDomains` to Strict-Transport-Security
- [ ] Optional: add /.well-known/security.txt with a contact email, once there's a real one
