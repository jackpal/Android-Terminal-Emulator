// Generate a repatable string of characters for fuzz testing.

package main

import (
	"fmt"
)

func setFore(c int) {
	fmt.Printf("\x1b[38;5;%dm", c)
}

func setBack(c int) {
	fmt.Printf("\x1b[48;5;%dm", c)
}

func main() {
	/*
		for i := 0; i < 255; i++ {
			setFore(i)
			fmt.Printf("Fore Color %d      \n", i)
		}
		setFore(0)
	*/

	for i := 0; i < 255; i++ {
		setBack(i)
		fmt.Printf("Back Color %d      \n", i)
	}
}
