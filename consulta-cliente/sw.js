// Royal Pontos — service worker: permite instalar o app e abrir mesmo sem internet.
const CACHE = "royal-pontos-v2";
const ARQUIVOS = ["./", "./index.html", "./config.js", "./manifest.webmanifest", "./logo-branco.png",
                  "./icone-192.png", "./icone-512.png", "./apple-touch-icon.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ARQUIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Primeiro tenta a internet (para pegar atualizações); sem internet, usa o que está guardado.
self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    fetch(req).then((resp) => {
      const copia = resp.clone();
      caches.open(CACHE).then((c) => c.put(req, copia));
      return resp;
    }).catch(() => caches.match(req).then((r) => r || caches.match("./index.html")))
  );
});
