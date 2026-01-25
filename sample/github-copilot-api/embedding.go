package githubcopilotapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
)

type EmbeddingRequest struct {
	Model string   `json:"model"`
	Input []string `json:"input"`
}

type EmbeddingResponse struct {
	Data []struct {
		Embedding []float32 `json:"embedding"`
		Index     int       `json:"index"`
	} `json:"data"`
	Usage struct {
		PromptTokens int `json:"prompt_tokens"`
		TotalTokens  int `json:"total_tokens"`
	} `json:"usage"`
}

func (c *Copilot) setEmbeddingDefaults(payload *EmbeddingRequest) {
	if payload.Model == "" {
		payload.Model = c.embeddingModel
	}
}

func (c *Copilot) CreateEmbedding(ctx context.Context, payload *EmbeddingRequest) (*EmbeddingResponse, error) {
	if err := c.withAuth(); err != nil {
		return nil, err
	}
	c.setEmbeddingDefaults(payload)

	payloadBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	// Build request
	body := bytes.NewReader(payloadBytes)
	fmt.Printf("body: %s\n", body)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, fmt.Sprintf("%s/embeddings", c.baseURL), body)

	// Print the request headers in a format that can be copied to Postman
	fmt.Println("Headers:")
	for key, values := range req.Header {
		for _, value := range values {
			fmt.Printf("%s: %s\n", key, value)
		}
	}
	fmt.Println("Copy the following headers to Postman:")
	for key, values := range req.Header {
		for _, value := range values {
			fmt.Printf("\"%s\": \"%s\",\n", key, value)
		}
	}

	// Print the request body
	bodyBytes, err := ioutil.ReadAll(req.Body)
	if err != nil {
		panic(err)
	}
	fmt.Println("Body:")
	fmt.Println(string(bodyBytes))

	if err != nil {
		return nil, err
	}

	c.setHeaders(req)
	fmt.Printf("req: %s\n", req.Header)
	// Send request
	r, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer r.Body.Close()

	if r.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("API returned unexpected status code: %d", r.StatusCode)
	}
	// Parse response
	var response EmbeddingResponse
	return &response, json.NewDecoder(r.Body).Decode(&response)
}
