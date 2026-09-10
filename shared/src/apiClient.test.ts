import { createApiConfiguration } from "./apiClient";

describe("createApiConfiguration", () => {
  it("carries the base path and credentials through", () => {
    const config = createApiConfiguration({
      basePath: "http://localhost:8080",
      getAccessToken: () => null,
      credentials: "include",
    });

    expect(config.basePath).toBe("http://localhost:8080");
    expect(config.credentials).toBe("include");
  });

  it("attaches the current access token as a Bearer header value", async () => {
    let token: string | null = "abc123";
    const config = createApiConfiguration({
      basePath: "http://localhost:8080",
      getAccessToken: () => token,
    });

    const resolved = await config.accessToken!("bearerAuth", []);
    expect(resolved).toBe("abc123");

    // Reads getAccessToken lazily each call, not just once at construction time.
    token = "different-token";
    const resolvedAgain = await config.accessToken!("bearerAuth", []);
    expect(resolvedAgain).toBe("different-token");
  });

  it("resolves to an empty string for a guest (no token) so no Authorization header is sent", async () => {
    const config = createApiConfiguration({
      basePath: "http://localhost:8080",
      getAccessToken: () => null,
    });

    const resolved = await config.accessToken!("bearerAuth", []);
    expect(resolved).toBe("");
  });
});
