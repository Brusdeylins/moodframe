#!/usr/bin/env bash
#
#   Updates the AUR package moodframe-bin to a version built by "./gradlew deploy": pkgver, pkgrel and
#   sha256sums of the PKGBUILD, then the .SRCINFO, without makepkg, so it runs on macOS, too. With --push,
#   it commits and pushes the package to the AUR, which needs the AUR account and SSH key of the maintainer.
#
#   Usage: packaging/aur/update.sh <version> [--push]
#

set -euo pipefail
shopt -s extglob

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
pkg="$here/moodframe-bin"
aur="ssh://aur@aur.archlinux.org/moodframe-bin.git"

version="${1:-}"
push="${2:-}"
if [[ $# -gt 2 || ! "$version" =~ ^[0-9]+(\.[0-9]+)*$ || ( -n "$push" && "$push" != "--push" ) ]]; then
    echo "usage: $0 <version> [--push]" >&2
    exit 1
fi
tarball="$root/build/packages/moodframe-$version/moodframe-$version-linux-amd64.tar.gz"
if [[ ! -f "$tarball" ]]; then
    echo "$0: $tarball is missing, build it with ./gradlew deploy first" >&2
    exit 1
fi

sha256() {
    if command -v sha256sum >/dev/null; then
        sha256sum "$1"
    else
        shasum -a 256 "$1"
    fi | cut -d " " -f 1
}

#   The .SRCINFO as "makepkg --printsrcinfo" writes it: the set attributes in the order of makepkg,
#   whitespace normalized; the package section is empty, as package() overrides no attribute
srcinfo() {
    (
        set +u
        source "$pkg/PKGBUILD"
        attr() {
            local ref="$1[@]"
            if declare -p "$1" >/dev/null 2>&1; then
                local values=("${!ref}")
                values=("${values[@]//+([[:space:]])/ }")
                values=("${values[@]#[[:space:]]}")
                values=("${values[@]%[[:space:]]}")
                printf "\t$1 = %s\n" "${values[@]}"
            fi
        }
        sums="cksums md5sums sha1sums sha224sums sha256sums sha384sums sha512sums b2sums"
        printf "pkgbase = %s\n" "${pkgbase:-$pkgname}"
        for name in pkgdesc pkgver pkgrel epoch url install changelog arch groups license checkdepends \
                makedepends depends optdepends provides conflicts replaces noextract options backup source \
                validpgpkeys $sums; do
            attr "$name"
        done
        for a in "${arch[@]}"; do
            [[ "$a" == "any" ]] && continue
            for name in source provides conflicts depends replaces optdepends makedepends checkdepends $sums; do
                attr "${name}_$a"
            done
        done
        printf "\npkgname = %s\n" "${pkgname[@]}"
    )
}

#   pkgver and pkgrel, then the checksums of the sources: the tarball from the build, the other files from here
sed -i.bak -e "s/^pkgver=.*/pkgver=$version/" -e "s/^pkgrel=.*/pkgrel=1/" "$pkg/PKGBUILD"
rm "$pkg/PKGBUILD.bak"
sums=""
files=()
while read -r entry; do
    if [[ "$entry" == *://* ]]; then
        if [[ "$entry" != */"${tarball##*/}" ]]; then
            echo "$0: unexpected download source $entry" >&2
            exit 1
        fi
        sum="$(sha256 "$tarball")"
    else
        sum="$(sha256 "$pkg/$entry")"
        files+=("$entry")
    fi
    sums="$sums${sums:+\\n            }'$sum'"
done < <(source "$pkg/PKGBUILD" && printf "%s\n" "${source[@]}")
awk -v sums="$sums" '
    /^sha256sums=\(/ { print "sha256sums=(" sums ")"; skip = !/\)/; next }
    skip             { skip = !/\)/; next }
                     { print }' "$pkg/PKGBUILD" >"$pkg/PKGBUILD.new"
mv "$pkg/PKGBUILD.new" "$pkg/PKGBUILD"
srcinfo >"$pkg/.SRCINFO"
echo "moodframe-bin $version-1: PKGBUILD and .SRCINFO updated"

#   Publishing is a step of its own: the AUR clone lives in build/aur/, not in this repository
if [[ "$push" == "--push" ]]; then
    clone="$root/build/aur/moodframe-bin"
    if [[ -d "$clone/.git" ]]; then
        git -C "$clone" pull --ff-only
    else
        mkdir -p "$root/build/aur"
        git clone "$aur" "$clone"
    fi
    cp -L "$pkg/PKGBUILD" "$pkg/.SRCINFO" "${files[@]/#/$pkg/}" "$clone/"
    git -C "$clone" add PKGBUILD .SRCINFO "${files[@]}"
    if git -C "$clone" diff --cached --quiet; then
        echo "moodframe-bin $version-1: already published"
    else
        git -C "$clone" commit -m "Update to $version"
        git -C "$clone" push origin HEAD:master
        echo "moodframe-bin $version-1: pushed to $aur"
    fi
fi
