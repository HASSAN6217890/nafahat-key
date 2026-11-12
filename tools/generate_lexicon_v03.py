#!/usr/bin/env python3
"""Bundled corpus generated at build time, never downloaded on a user's phone."""
import os
from wordfreq import top_n_list,zipf_frequency
os.makedirs("app/src/main/assets",exist_ok=True)
for language,target in (("ar",50000),("en",30000)):
    selected=[]
    for word in top_n_list(language,target*5):
        if not word.isalpha() or len(word)<2 or len(word)>40:continue
        selected.append(word)
        if len(selected)>=target:break
    if len(selected)<target:raise RuntimeError(f"{language}: only {len(selected)} usable words")
    with open(f"app/src/main/assets/lexicon_{language}.tsv","w",encoding="utf-8") as file:
        for word in selected:
            score=max(10,min(110,round(zipf_frequency(word,language)*10)))
            file.write(f"{word}\t{score}\n")
    print(f"generated {language}: {len(selected)} entries")
