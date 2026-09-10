import { useEffect, useState, type FormEvent } from "react";
import { teamsApi, leagueTypesApi } from "../lib/auth";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";
import type { TeamResponse, LeagueTypeResponse } from "@fc27/shared";
import { TeamCrest } from "../components/TeamCrest";

const STAR_LEVELS = [2, 3, 4, 5];
const EMPTY_FORM = { name: "", code: "", colorHex: "#0f9a82", starLevel: 3, leagueTypeId: "" };

async function extractErrorMessage(err: unknown): Promise<string> {
  if (err instanceof ResponseError) {
    try {
      const body = await err.response.json();
      if (typeof body?.message === "string") return body.message;
    } catch {
      // fall through
    }
  }
  return "Bir şeyler ters gitti.";
}

export function AdminTeamsPage() {
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [leagueTypes, setLeagueTypes] = useState<LeagueTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [newLeagueTypeName, setNewLeagueTypeName] = useState("");
  const [leagueTypeError, setLeagueTypeError] = useState<string | null>(null);

  async function reload() {
    // includeInactive: without it, a just-deactivated team vanishes from this list too
    // (the plain GET /api/teams the public wheel uses only ever returns active ones),
    // leaving no way to see it actually worked.
    const [teamList, leagueTypeList] = await Promise.all([
      teamsApi.listTeams({ includeInactive: true }),
      leagueTypesApi.listLeagueTypes(),
    ]);
    setTeams(teamList);
    setLeagueTypes(leagueTypeList);
  }

  useEffect(() => {
    reload()
      .catch((err) => {
        logWarn("admin.loadFailed", { error: String(err) });
        setPageError("Takımlar/lig türleri yüklenemedi. Sunucu çalışıyor mu?");
      })
      .finally(() => setLoading(false));
  }, []);

  function startCreate() {
    setEditingId(0);
    setForm(EMPTY_FORM);
    setFormError(null);
  }

  function startEdit(team: TeamResponse) {
    setEditingId(team.id!);
    setForm({
      name: team.name ?? "",
      code: team.code ?? "",
      colorHex: team.colorHex ?? "#0f9a82",
      starLevel: team.starLevel ?? 3,
      leagueTypeId: String(team.leagueTypeId ?? ""),
    });
    setFormError(null);
  }

  function cancelForm() {
    setEditingId(null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    setSaving(true);
    const teamCreateRequest = {
      name: form.name,
      code: form.code,
      colorHex: form.colorHex,
      starLevel: form.starLevel,
      leagueTypeId: Number(form.leagueTypeId),
    };
    try {
      if (editingId) {
        await teamsApi.replaceTeam({ id: editingId, teamCreateRequest });
        logInfo("admin.teamUpdated", { id: editingId });
      } else {
        const created = await teamsApi.createTeam({ teamCreateRequest });
        logInfo("admin.teamCreated", { id: created.id });
      }
      setEditingId(null);
      await reload();
    } catch (err) {
      setFormError(await extractErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDeactivate(team: TeamResponse) {
    if (!team.id) return;
    if (!confirm(`${team.name} devre dışı bırakılsın mı? Çarktan gizlenecek ama maç geçmişi korunacak.`)) return;
    try {
      await teamsApi.deactivateTeam({ id: team.id });
      logInfo("admin.teamDeactivated", { id: team.id });
      await reload();
    } catch (err) {
      logWarn("admin.deactivateFailed", { error: String(err) });
      alert(await extractErrorMessage(err));
    }
  }

  async function handleAddLeagueType(event: FormEvent) {
    event.preventDefault();
    setLeagueTypeError(null);
    try {
      await leagueTypesApi.createLeagueType({ leagueTypeCreateRequest: { name: newLeagueTypeName } });
      setNewLeagueTypeName("");
      await reload();
    } catch (err) {
      setLeagueTypeError(await extractErrorMessage(err));
    }
  }

  if (loading) {
    return <div className="px-4 py-8 text-center text-sm text-text-faint">Yükleniyor…</div>;
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <h1 className="mb-6 text-2xl font-bold text-text">Yönetici: Takımlar</h1>
      {pageError && <p className="mb-4 text-sm font-semibold text-loss">{pageError}</p>}

      <section className="mb-6 rounded-2xl border border-border-soft bg-surface p-5">
        <h2 className="mb-3 text-sm font-bold text-text">Lig türleri</h2>
        <div className="mb-3 flex flex-wrap gap-2">
          {leagueTypes.map((lt) => (
            <span key={lt.id} className="rounded-full border border-border bg-surface-2 px-3 py-1 text-xs font-semibold text-text-dim">
              {lt.name}
            </span>
          ))}
        </div>
        <form onSubmit={handleAddLeagueType} className="flex gap-2">
          <input
            value={newLeagueTypeName}
            onChange={(e) => setNewLeagueTypeName(e.target.value)}
            placeholder="Yeni lig türü, örn. Serie A"
            required
            className="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-text"
          />
          <button type="submit" className="rounded-lg bg-accent px-4 text-sm font-bold text-accent-ink">
            Ekle
          </button>
        </form>
        {leagueTypeError && <p className="mt-2 text-sm font-semibold text-loss">{leagueTypeError}</p>}
      </section>

      <section className="rounded-2xl border border-border-soft bg-surface p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-bold text-text">Takımlar ({teams.length})</h2>
          {editingId === null && (
            <button
              type="button"
              onClick={startCreate}
              className="rounded-lg bg-accent px-3.5 py-2 text-xs font-bold text-accent-ink"
            >
              + Takım ekle
            </button>
          )}
        </div>

        {editingId !== null && (
          <form onSubmit={handleSubmit} className="mb-5 grid grid-cols-1 gap-3 rounded-xl border border-border-soft bg-surface-2 p-4 sm:grid-cols-2">
            <label className="text-xs font-bold text-text-dim">
              İsim
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="mt-1 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
              />
            </label>
            <label className="text-xs font-bold text-text-dim">
              Kod (1-5 harf)
              <input
                required
                maxLength={5}
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                className="mt-1 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text uppercase"
              />
            </label>
            <label className="text-xs font-bold text-text-dim">
              Yıldız seviyesi
              <select
                value={form.starLevel}
                onChange={(e) => setForm({ ...form, starLevel: Number(e.target.value) })}
                className="mt-1 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
              >
                {STAR_LEVELS.map((s) => (
                  <option key={s} value={s}>
                    {s} yıldız
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-bold text-text-dim">
              Lig türü
              <select
                required
                value={form.leagueTypeId}
                onChange={(e) => setForm({ ...form, leagueTypeId: e.target.value })}
                className="mt-1 w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text"
              >
                <option value="" disabled>
                  Seç…
                </option>
                {leagueTypes.map((lt) => (
                  <option key={lt.id} value={lt.id}>
                    {lt.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-bold text-text-dim sm:col-span-2">
              Forma rengi
              <input
                type="color"
                value={form.colorHex}
                onChange={(e) => setForm({ ...form, colorHex: e.target.value })}
                className="mt-1 h-9 w-16 rounded border border-border bg-surface"
              />
            </label>

            {formError && <p className="text-sm font-semibold text-loss sm:col-span-2">{formError}</p>}

            <div className="flex gap-2 sm:col-span-2">
              <button type="submit" disabled={saving} className="rounded-lg bg-accent px-4 py-2 text-sm font-bold text-accent-ink disabled:opacity-50">
                {saving ? "Kaydediliyor…" : editingId ? "Değişiklikleri kaydet" : "Takım oluştur"}
              </button>
              <button type="button" onClick={cancelForm} className="rounded-lg border border-border px-4 py-2 text-sm font-bold text-text-dim">
                Vazgeç
              </button>
            </div>
          </form>
        )}

        <div className="flex flex-col divide-y divide-border-soft">
          {teams.map((team) => (
            <div key={team.id} className="flex items-center gap-3 py-3">
              <TeamCrest code={team.code} colorHex={team.colorHex} size="sm" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-bold text-text">{team.name}</div>
                <div className="text-xs text-text-faint">
                  {team.leagueTypeName} · {"★".repeat(team.starLevel ?? 0)}
                  {!team.active && <span className="ml-2 text-loss">pasif</span>}
                </div>
              </div>
              <button type="button" onClick={() => startEdit(team)} className="rounded-lg border border-border px-3 py-1.5 text-xs font-bold text-text-dim">
                Düzenle
              </button>
              {team.active && (
                <button
                  type="button"
                  onClick={() => handleDeactivate(team)}
                  className="rounded-lg border border-loss px-3 py-1.5 text-xs font-bold text-loss"
                >
                  Devre dışı bırak
                </button>
              )}
            </div>
          ))}
          {teams.length === 0 && <p className="py-6 text-center text-sm text-text-faint">Henüz takım yok.</p>}
        </div>
      </section>
    </div>
  );
}
