// Runtime-only test probe: no account data or subscription requests.
package main

import (
	"context"
	"crypto/x509"
	"encoding/json"
	"net"
	"net/http"
	"os"
	"runtime/debug"
	"time"
)

func main() {
	result := map[string]bool{"cgo": false, "dns": false, "system_roots": false, "https": false}
	if info, ok := debug.ReadBuildInfo(); ok {
		for _, setting := range info.Settings {
			if setting.Key == "CGO_ENABLED" {
				result["cgo"] = setting.Value == "1"
			}
		}
	}
	roots, err := x509.SystemCertPool()
	result["system_roots"] = err == nil && len(roots.Subjects()) > 0
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	addresses, err := net.DefaultResolver.LookupHost(ctx, "example.com")
	result["dns"] = err == nil && len(addresses) > 0
	req, _ := http.NewRequestWithContext(ctx, http.MethodGet, "https://example.com/", nil)
	client := &http.Client{Timeout: 20 * time.Second, CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse }}
	response, err := client.Do(req)
	if err == nil {
		result["https"] = response.TLS != nil && len(response.TLS.VerifiedChains) > 0 && response.StatusCode == 200
		response.Body.Close()
	}
	json.NewEncoder(os.Stdout).Encode(result)
	for _, ok := range result {
		if !ok {
			os.Exit(1)
		}
	}
}
