from pathlib import Path
root = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/synthesis/ui/components/exoplanet'
for tic in ('167418703', '141913165'):
    html = (root / f'Tic{tic}RechartsHtmlBuilder.kt').read_text()
    view = (root / f'Tic{tic}RechartsView.kt').read_text()
    assert 'Raport demonstrativ dezactivat' in html
    assert 'generateTransitPoints(' not in html and 'generateSymmetryPoints(' not in html
    assert 'TLS nevalidat' in html and 'https://' not in html
    assert 'Raportul demonstrativ a fost dezactivat' in view
    print(f'TIC {tic}: 4 static checks passed')
