import { act, create, ReactTestRenderer } from "react-test-renderer";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "@/features/auth/contexts/AuthContext";
import { RoleSwitcher } from "@/shared/components/RoleSwitcher/RoleSwitcher";

vi.mock("@/features/auth/demo", () => ({
  demoLoginEnabled: true,
  demoPassword: "test-password",
  demoRolePresets: [
    { email: "demo.nv1@example.com", role: "ADMIN", label: "Administrator" },
    { email: "demo.nv2@example.com", role: "IT_STAFF", label: "IT Staff" },
    {
      email: "demo.nv3@example.com",
      role: "PUR_STAFF",
      label: "Purchasing Staff",
    },
    { email: "demo.nv4@example.com", role: "USER", label: "End User" },
  ],
}));
vi.mock("react-router-dom", () => ({ useNavigate: () => vi.fn() }));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const storage = new Map<string, string>();
let tree: ReactTestRenderer | undefined;

beforeEach(() => {
  vi.stubGlobal("window", { addEventListener() {}, removeEventListener() {} });
  vi.stubGlobal("document", {
    addEventListener() {},
    removeEventListener() {},
  });
});

afterEach(() => {
  if (tree) act(() => tree!.unmount());
  tree = undefined;
  storage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("demo account switching", () => {
  it("shows the same configured demo accounts as quick login", async () => {
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    });
    const current = {
      id: 1,
      email: "demo.nv1@example.com",
      fullName: "Admin",
      role: "ADMIN",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            success: true,
            data: { token: "admin-token", user: current },
          }),
        ),
      ),
    );
    let auth!: ReturnType<typeof useAuth>;
    const Probe = () => {
      auth = useAuth();
      return <RoleSwitcher />;
    };
    await act(async () => {
      tree = create(
        <AuthProvider>
          <Probe />
        </AuthProvider>,
      );
    });
    await act(async () =>
      auth.login({ email: current.email, password: "test-password" }),
    );
    act(() =>
      tree!.root
        .findByProps({ className: "role-switcher-trigger" })
        .props.onClick(),
    );
    const addresses = tree!.root
      .findAllByProps({ className: "role-switcher-item-info" })
      .map((item) => item.findAllByType("small")[0].children.join(""));
    expect(addresses).toEqual([
      "demo.nv1@example.com",
      "demo.nv2@example.com",
      "demo.nv3@example.com",
      "demo.nv4@example.com",
    ]);
  });

  it("keeps the current session when switching accounts fails", async () => {
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    });
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            success: true,
            data: {
              token: "admin-token",
              user: {
                id: 1,
                email: "demo.nv1@example.com",
                fullName: "Admin",
                role: "ADMIN",
              },
            },
          }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ message: "Invalid credentials" }), {
          status: 401,
        }),
      );
    vi.stubGlobal("fetch", fetch);
    let auth!: ReturnType<typeof useAuth>;
    const Probe = () => {
      auth = useAuth();
      return null;
    };
    await act(async () => {
      tree = create(
        <AuthProvider>
          <Probe />
        </AuthProvider>,
      );
    });
    await act(async () =>
      auth.login({ email: "demo.nv1@example.com", password: "test-password" }),
    );
    await act(async () => {
      await expect(auth.switchRole("demo.nv2@example.com")).rejects.toThrow();
    });
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      email: "demo.nv2@example.com",
      password: "test-password",
    });
    expect(auth.user?.email).toBe("demo.nv1@example.com");
    expect(storage.get("itam_auth_token")).toBe("admin-token");
  });
});
