import { useQueryClient } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Providers } from "./providers";

afterEach(cleanup);

function Probe() {
  const client = useQueryClient();
  return (
    <p>
      mutations retry: {String(client.getDefaultOptions().mutations?.retry)}
    </p>
  );
}

describe("Providers", () => {
  it("안의 컴포넌트가 QueryClient 를 받고 mutations retry 가 0 이다", () => {
    render(
      <Providers>
        <Probe />
      </Providers>,
    );
    expect(screen.getByText("mutations retry: 0")).toBeInTheDocument();
  });
});
