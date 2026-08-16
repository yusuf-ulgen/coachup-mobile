import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const SLOT_MS = 30000

/** Same algorithm as frontend buildManualCode — 6-digit PIN */
function buildManualCode(gymId: string, slot: number, type: 'entry' | 'exit' = 'entry'): string {
  const seed = `${type === 'exit' ? 'EXIT' : 'ENTRY'}:${gymId}:${slot}`
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return String((h >>> 0) % 1000000).padStart(6, '0')
}

function allowedSlots(): number[] {
  const currentSlot = Math.floor(Date.now() / SLOT_MS)
  return [currentSlot - 1, currentSlot, currentSlot + 1]
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY') || Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

    const supabase = createClient(supabaseUrl, supabaseServiceKey)

    // ── 1. AUTHORIZATION BEARER TOKEN VERIFICATION ────────────────────────────
    const authHeader = req.headers.get('Authorization')
    let authenticatedUserId: string | null = null

    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.replace('Bearer ', '').trim()
      const clientWithToken = createClient(supabaseUrl, supabaseAnonKey, {
        global: { headers: { Authorization: `Bearer ${token}` } }
      })
      const { data: authData, error: authErr } = await clientWithToken.auth.getUser()
      if (!authErr && authData?.user?.id) {
        authenticatedUserId = authData.user.id
      }
    }

    const payload = await req.json()
    const { qr, user_id } = payload || {}

    if (!qr) {
      return new Response(JSON.stringify({ error: 'qr is required' }), {
        status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // Effective user ID: server-side authenticated user takes precedence or must match
    const effectiveUserId = authenticatedUserId || user_id
    if (!effectiveUserId) {
      return new Response(JSON.stringify({ error: 'Unauthorized: user identity could not be resolved' }), {
        status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    if (authenticatedUserId && user_id && authenticatedUserId !== user_id) {
      return new Response(JSON.stringify({ error: 'Forbidden: user_id mismatch with bearer token' }), {
        status: 403, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const raw = String(qr).trim()
    let gymId: string | null = null
    let slot: number | null = null
    let matchedBy: 'full' | 'manual' = 'full'

    // Full format: COACHUP_ENTRY:<gym_id>:<slot>
    const parts = raw.split(':')
    if (parts.length >= 3 && parts[0] === 'COACHUP_ENTRY') {
      gymId = parts[1]
      slot = Number(parts[2])
      if (!gymId || Number.isNaN(slot)) {
        return new Response(JSON.stringify({ accepted: false, reason: 'invalid_parts' }), {
          status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }
      if (!allowedSlots().includes(slot)) {
        return new Response(JSON.stringify({ accepted: false, reason: 'slot_mismatch' }), {
          status: 403, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }
    } else if (/^\d{6}$/.test(raw)) {
      // 6-digit manual PIN — resolve gym from user, match against current slots
      matchedBy = 'manual'
      const { data: userRow, error: userErr } = await supabase
        .from('users')
        .select('id, gym_id')
        .eq('id', effectiveUserId)
        .maybeSingle()

      if (userErr || !userRow?.gym_id) {
        return new Response(JSON.stringify({ accepted: false, reason: 'user_gym_not_found' }), {
          status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      gymId = userRow.gym_id
      for (const s of allowedSlots()) {
        if (buildManualCode(gymId, s, 'entry') === raw) {
          slot = s
          break
        }
      }
      if (slot == null) {
        return new Response(JSON.stringify({ accepted: false, reason: 'slot_mismatch' }), {
          status: 403, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }
    } else {
      return new Response(JSON.stringify({ accepted: false, reason: 'invalid_format' }), {
        status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const now = new Date().toISOString()
    const dateStr = now.split('T')[0]
    const timeStr = now.split('T')[1]?.split('.')[0]
    const insertPayload: Record<string, unknown> = {
      gym_id: gymId,
      entry_method: matchedBy === 'manual' ? 'manual' : 'qr',
      entry_timestamp: now,
      entry_date: dateStr,
      entry_time: timeStr,
      code: raw,
      user_id: effectiveUserId,
    }

    const { error } = await supabase.from('qr_entries').insert(insertPayload)

    if (error) {
      return new Response(JSON.stringify({ accepted: false, reason: 'insert_failed', error: error.message }), {
        status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    return new Response(JSON.stringify({ accepted: true, gym_id: gymId }), {
      status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
