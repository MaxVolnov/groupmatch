import './promo.css'

// Smooth scroll for in-page anchor links
document.addEventListener('click', (e) => {
  const target = e.target as HTMLElement
  const anchor = target.closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!anchor) return

  const id = anchor.getAttribute('href')?.slice(1)
  if (!id) return

  const el = document.getElementById(id)
  if (!el) return

  e.preventDefault()
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
})

// Forward UTM params to all signup CTAs
document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(location.search)
  const utmParams = new URLSearchParams()
  params.forEach((value, key) => {
    if (key.startsWith('utm_')) utmParams.append(key, value)
  })
  if (utmParams.toString().length === 0) return

  document.querySelectorAll<HTMLAnchorElement>('a[data-cta]').forEach((cta) => {
    // Read the literal attribute, not the .href property: the property
    // resolves against the current origin, so on www.groupmatch.app it would
    // bake the www hostname into the link. Write back a relative path so the
    // CTA always stays on whatever origin the visitor is already on.
    const raw = cta.getAttribute('href')
    if (!raw) return
    const url = new URL(raw, location.origin)
    utmParams.forEach((value, key) => url.searchParams.set(key, value))
    cta.setAttribute('href', `${url.pathname}${url.search}${url.hash}`)
  })
})
