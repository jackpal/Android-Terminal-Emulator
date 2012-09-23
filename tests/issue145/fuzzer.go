// Generate a repatable string of characters for fuzz testing.

package main

import (
	"fmt"
	"math/rand"
	"unicode"
)

func main() {
	source := rand.NewSource(17)
	r := rand.New(source)

	for {
		switch r.Intn(4) {
		case 0:
			if r.Intn(20) == 0 {
				fmt.Printf("\n")
			} else {
				out(r, 32, 128)
			}
		case 1:
			out(r, 128, 256)
		case 2:
			out(r, 256, 0x10000)
		default:
			out(r, 0x10000, unicode.MaxRune)
		}
	}
}

func out(r *rand.Rand, begin, end int) {
	var c rune
	c = (rune)(r.Intn(end-begin) + begin)
	fmt.Printf("%c", c)
}
