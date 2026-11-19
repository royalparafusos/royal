"""Baixa as fotos dos prêmios listados em premios/fontes.json e gera consulta-cliente/premios/."""
import io, json, re, sys, time, urllib.parse
from pathlib import Path
import requests
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / "consulta-cliente" / "premios"
DESTINO.mkdir(parents=True, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
      "Accept-Language": "pt-BR,pt;q=0.9"}
S = requests.Session(); S.headers.update(UA)

def baixar_imagem(url):
    try:
        r = S.get(url, timeout=25)
        if r.status_code != 200 or len(r.content) < 3000: return None
        img = Image.open(io.BytesIO(r.content))
        img.load()
        if min(img.size) < 120: return None
        return img
    except Exception as e:
        print("   falhou", url[:90], e); return None

def og_image(pagina):
    try:
        h = S.get(pagina, timeout=25).text
    except Exception as e:
        print("   página falhou", e); return []
    achados = re.findall(r'<meta[^>]+(?:property|name)=["\'](?:og:image|twitter:image)(?::src)?["\'][^>]+content=["\']([^"\']+)', h, re.I)
    achados += re.findall(r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+(?:property|name)=["\'](?:og:image|twitter:image)', h, re.I)
    lista = [urllib.parse.urljoin(pagina, a.replace("&amp;", "&")) for a in achados]
    return lista + imagens_da_pagina(h, pagina)

LOG = []
_print = print
def print(*a):
    _print(*a); LOG.append(" ".join(str(x) for x in a))

def imagens_da_pagina(h, pagina):
    """Fotos grandes da página quando não há og:image (ex.: lojas que montam a página por JavaScript)."""
    urls = re.findall(r'(?:src|data-src|data-zoom-image|href)=["\']([^"\']+\.(?:jpe?g|png|webp)(?:\?[^"\']*)?)["\']', h, re.I)
    urls += re.findall(r'"(https?:[^"]+\.(?:jpe?g|png|webp)[^"]*)"', h, re.I)
    ruins = ("logo", "icon", "banner", "sprite", "selo", "bandeira", "pagamento", "avatar", "placeholder")
    vistos, out = set(), []
    for u in urls:
        u = urllib.parse.urljoin(pagina, u.replace("\\/", "/").replace("&amp;", "&"))
        if u in vistos or any(r in u.lower() for r in ruins): continue
        vistos.add(u); out.append(u)
    return out[:12]

def busca_bing(q):
    try:
        r = S.get("https://www.bing.com/images/search", params={"q": q, "form": "HDRSC2", "first": "1"}, timeout=20)
        achados = re.findall(r'murl&quot;:&quot;(.*?)&quot;', r.text) or re.findall(r'"murl":"(.*?)"', r.text)
        return [a.replace("\\/", "/") for a in achados[:8]]
    except Exception as e:
        print("   bing falhou", e); return []

def busca_ml(q):
    """Busca pública do Mercado Livre (fotos de anúncios)."""
    out = []
    try:
        r = S.get("https://api.mercadolibre.com/sites/MLB/search", params={"q": q, "limit": 6}, timeout=20)
        print("   ML status", r.status_code)
        for x in r.json().get("results", []):
            t = (x.get("thumbnail") or "").replace("http://", "https://")
            if t: out.append(re.sub(r"-[A-Z]\.(jpg|webp)$", r"-O.\1", t))
    except Exception as e:
        print("   ML falhou", e)
    if not out:
        try:
            h = S.get("https://lista.mercadolivre.com.br/" + urllib.parse.quote(q.replace(" ", "-")), timeout=20).text
            out = [u for u in re.findall(r'(https://http2\.mlstatic\.com/D_[^"\s]+?\.(?:jpg|webp))', h) if "-O." in u or "-V." in u or "_2X_" in u][:6]
            print("   ML lista", len(out))
        except Exception as e:
            print("   ML lista falhou", e)
    return out

def busca_ddg(q):
    try:
        r = S.get("https://duckduckgo.com/", params={"q": q, "iax": "images", "ia": "images"}, timeout=20)
        vqd = re.search(r'vqd=["\']?([\d-]+)', r.text)
        if not vqd: return []
        r = S.get("https://duckduckgo.com/i.js", params={"l": "br-pt", "o": "json", "q": q, "vqd": vqd.group(1), "f": ",,,", "p": "1"},
                  headers={"Referer": "https://duckduckgo.com/"}, timeout=20)
        return [x["image"] for x in r.json().get("results", [])[:8]]
    except Exception as e:
        print("   busca falhou", e); return []

def salvar(img, arq):
    img = img.convert("RGBA")
    fundo = Image.new("RGBA", img.size, (255, 255, 255, 255)); fundo.alpha_composite(img)
    img = fundo.convert("RGB")
    img.thumbnail((640, 640), Image.LANCZOS)
    lado = max(img.size); quadro = Image.new("RGB", (lado, lado), (255, 255, 255))
    quadro.paste(img, ((lado - img.width) // 2, (lado - img.height) // 2))
    quadro.save(arq, "JPEG", quality=86, optimize=True)

fontes = json.loads((RAIZ / "premios" / "fontes.json").read_text(encoding="utf-8"))
saida = []
for it in fontes["itens"]:
    arq = DESTINO / f"{it['gtin']}.jpg"
    print(it["gtin"], it["nome"])
    origem = None
    if not arq.exists() or it.get("refazer"):
        candidatos = [("link", u) for u in it.get("imagens", [])]
        for p in it.get("paginas", []): candidatos += [("página", u) for u in og_image(p)]
        if it.get("busca"):
            candidatos += [("ml", u) for u in busca_ml(it["busca"])]
            candidatos += [("bing", u) for u in busca_bing(it["busca"])]
            candidatos += [("ddg", u) for u in busca_ddg(it["busca"])]
        print("   candidatos:", len(candidatos))
        for tipo, u in candidatos:
            img = baixar_imagem(u)
            if img:
                salvar(img, arq); origem = f"{tipo}: {u}"; print("   OK", origem[:120]); break
            time.sleep(0.5)
        else:
            print("   SEM IMAGEM")
    if arq.exists():
        saida.append({"gtin": it["gtin"], "nome": it["nome"], "bling": it.get("bling", []), "palavras": it.get("palavras", []),
                      "imagem": f"premios/{it['gtin']}.jpg"})
(DESTINO / "imagens.json").write_text(json.dumps({"itens": saida}, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(saida), "imagens prontas")
(DESTINO / "log.txt").write_text("\n".join(LOG), encoding="utf-8")
