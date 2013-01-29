// Stuff newly defined English strings into a local file. Makes it easier to localize.

package main

import (
	// "encoding/xml"
	"flag"
	"fmt"
	// "io"
	"io/ioutil"
	"log"
	"os"
	"path"
	"path/filepath"
)

var rootDir *string = flag.String("root", "", "Root directory")

var locale *string = flag.String("locale", "", "Locale to update")

func main() {
	err := process()
	if err != nil {
		log.Fatal(err)
	}
}

func process() (err error) {
	flag.Parse()
	if *rootDir == "" {
		err = fmt.Errorf("Must define --root")
		return
	}
	if *locale == "" {
		err = fmt.Errorf("Must define --locale")
		return
	}

	resPath := path.Join(*rootDir, "res")
	basePath := path.Join(resPath, "values")
	localePath := path.Join(resPath, "values-"+*locale)

	for file := range flag.Args() {
		err := update(path.Join(basePath, file), path.Join(localePath, file))
		if err != nil {
			return
		}
	}
	return
}

func update(baseFilePath, localeFilePath string) (err error) {
	var baseFile, localeFile, tmpFile *os.File
	baseFile, err = os.Open(baseFilePath)
	if err != nil {
		return
	}
	defer baseFile.Close()
	localeFile, err = os.Open(localeFile)
	if err != nil {
		return
	}
	defer localeFile.Close()
	localeDir, _ := path.Split(localeFilePath)
	tmpFile, err = ioutil.TempFile(localeDir, "xmlOut")
	if err != nil {
		return
	}
	read
	defer localeFile.Close()
	return
}
