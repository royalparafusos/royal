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
    return [urllib.parse.urljoin(pagina, a.replace("&amp;", "&")) for a in achados]

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
        if it.get("busca"): candidatos += [("busca", u) for u in busca_ddg(it["busca"])]
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
