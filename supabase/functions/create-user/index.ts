import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-user-jwt',
}

function getRequestMeta(req: Request) {
  return {
    ip: req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || req.headers.get('cf-connecting-ip') || null,
    userAgent: req.headers.get('user-agent') || null,
  }
}

async function writeAudit(supabase: ReturnType<typeof createClient>, entry: Record<string, unknown>) {
  try {
    await supabase.from('audit_logs').insert(entry)
  } catch (_) { /* audit failure must not block */ }
}

function generateSecureRandomPassword(length = 16): string {
  const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+"
  const array = new Uint8Array(length)
  crypto.getRandomValues(array)
  let password = ""
  for (let i = 0; i < length; i++) {
    password += chars[array[i] % chars.length]
  }
  return password
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY') || ''

    const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey)

    // 1. Resolve caller authorization token
    const authHeader = req.headers.get('Authorization') || ''
    const customUserJwt = req.headers.get('x-user-jwt') || ''
    const rawJwt = customUserJwt || authHeader.replace(/^Bearer\s+/i, '').trim()

    if (!rawJwt || rawJwt === anonKey || rawJwt === serviceRoleKey) {
      return new Response(JSON.stringify({ error: 'Yetkisiz erişim: Geçerli kullanıcı oturumu gereklidir.' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // 2. Validate token and resolve caller user
    const { data: userData, error: userAuthErr } = await supabaseAdmin.auth.getUser(rawJwt)
    if (userAuthErr || !userData?.user) {
      return new Response(JSON.stringify({ error: 'Geçersiz veya süresi dolmuş oturum.' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const callerUser = userData.user
    const callerId = callerUser.id
    const callerEmail = callerUser.email || null

    // 3. Resolve caller permissions from users table
    const { data: callerProfile, error: profileErr } = await supabaseAdmin
      .from('users')
      .select('id, email, is_admin, is_gym_manager, managed_gym_id')
      .eq('id', callerId)
      .single()

    if (profileErr || !callerProfile) {
      return new Response(JSON.stringify({ error: 'Kullanıcı profil yetkisi doğrulanamadı.' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const isCallerSuperAdmin = callerProfile.is_admin === true
    const isCallerGymManager = callerProfile.is_gym_manager === true
    const callerManagedGymId = callerProfile.managed_gym_id

    if (!isCallerSuperAdmin && !isCallerGymManager) {
      return new Response(JSON.stringify({ error: 'Bu işlem için yönetici yetkisi gereklidir.' }), {
        status: 403,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const body = await req.json()
    const { action } = body
    const { ip, userAgent } = getRequestMeta(req)

    // ─── ACTION: CREATE USER ───────────────────────────────────────────
    if (action === 'create') {
      const { email, name, surname, is_admin, gym_id, is_gym_manager } = body

      if (!email || !email.includes('@')) {
        return new Response(JSON.stringify({ error: 'Geçerli bir e-posta adresi gereklidir.' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // Tenant Validation: Gym manager can only create users for their managed gym
      const targetGymId = gym_id || callerManagedGymId
      if (!isCallerSuperAdmin) {
        if (!targetGymId || targetGymId !== callerManagedGymId) {
          return new Response(JSON.stringify({ error: 'Yalnızca kendi salonunuza üye ekleyebilirsiniz.' }), {
            status: 403,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
        if (is_admin === true) {
          return new Response(JSON.stringify({ error: 'Admin kullanıcısı oluşturma yetkiniz yoktur.' }), {
            status: 403,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      // Generate secure temporary password server-side (never use hardcoded static password)
      const securePassword = body.password && body.password.length >= 8 ? body.password : generateSecureRandomPassword(16)

      const normalizedEmail = email.trim().toLowerCase()

      const { data: authData, error: authError } = await supabaseAdmin.auth.admin.createUser({
        email: normalizedEmail,
        password: securePassword,
        email_confirm: true,
        user_metadata: { name: name?.trim(), surname: surname?.trim() },
      })

      // Build safe update payload
      const reserved = new Set(['action', 'email', 'password'])
      const updatePayload: Record<string, unknown> = {
        name: name?.trim(),
        surname: surname?.trim(),
        gym_id: targetGymId || null,
        is_admin: isCallerSuperAdmin ? (is_admin || false) : false,
        is_gym_manager: isCallerSuperAdmin ? (is_gym_manager || false) : false,
      }

      for (const [k, v] of Object.entries(body)) {
        if (!reserved.has(k) && k !== 'name' && k !== 'surname' && k !== 'gym_id' && k !== 'is_admin' && k !== 'is_gym_manager') {
          updatePayload[k] = v
        }
      }

      let userId: string

      if (authError) {
        const alreadyExists =
          authError.message.includes('already registered') ||
          authError.message.includes('already been registered') ||
          authError.status === 422

        if (!alreadyExists) {
          return new Response(JSON.stringify({ error: authError.message, code: authError.status }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }

        const { data: listData } = await supabaseAdmin.auth.admin.listUsers({ perPage: 1000 })
        const existingAuthUser = listData?.users?.find(u => u.email?.toLowerCase() === normalizedEmail)

        if (!existingAuthUser) {
          return new Response(JSON.stringify({ error: 'Mevcut kullanıcı tespit edilemedi.' }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }

        userId = existingAuthUser.id
      } else {
        userId = authData.user.id
      }

      const { error: upsertErr } = await supabaseAdmin.from('users').upsert({
        id: userId,
        email: normalizedEmail,
        ...updatePayload,
      })

      if (upsertErr) {
        return new Response(JSON.stringify({ error: upsertErr.message }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      await writeAudit(supabaseAdmin, {
        user_id: callerId,
        user_email: callerEmail,
        user_role: isCallerSuperAdmin ? 'admin' : 'gym_manager',
        action: 'create',
        resource_type: 'user',
        resource_id: userId,
        description: `Kullanıcı oluşturuldu: ${normalizedEmail}`,
        gym_id: targetGymId || null,
        ip_address: ip,
        user_agent: userAgent,
        status: 'success',
      })

      return new Response(JSON.stringify({ user: { id: userId, email: normalizedEmail } }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // ─── ACTION: UPDATE PASSWORD ───────────────────────────────────────
    if (action === 'update_password') {
      const { user_id: targetUserId, password: newPassword } = body

      if (!targetUserId || !newPassword || newPassword.length < 6) {
        return new Response(JSON.stringify({ error: 'Geçerli bir kullanıcı ID ve en az 6 karakterli şifre gereklidir.' }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      // Authorization Check
      if (!isCallerSuperAdmin) {
        const { data: targetProfile } = await supabaseAdmin
          .from('users')
          .select('id, gym_id')
          .eq('id', targetUserId)
          .single()

        if (!targetProfile || targetProfile.gym_id !== callerManagedGymId) {
          return new Response(JSON.stringify({ error: 'Yalnızca kendi salonunuzdaki üyelerin şifresini güncelleyebilirsiniz.' }), {
            status: 403,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          })
        }
      }

      const { error: updateAuthErr } = await supabaseAdmin.auth.admin.updateUserById(targetUserId, {
        password: newPassword,
      })

      if (updateAuthErr) {
        return new Response(JSON.stringify({ error: updateAuthErr.message }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        })
      }

      await writeAudit(supabaseAdmin, {
        user_id: callerId,
        user_email: callerEmail,
        user_role: isCallerSuperAdmin ? 'admin' : 'gym_manager',
        action: 'update_password',
        resource_type: 'user',
        resource_id: targetUserId,
        description: `Kullanıcı şifresi sıfırlandı: ${targetUserId}`,
        ip_address: ip,
        user_agent: userAgent,
        status: 'success',
      })

      return new Response(JSON.stringify({ success: true, message: 'Şifre başarıyla güncellendi.' }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    return new Response(JSON.stringify({ error: 'Geçersiz işlem tipi.' }), {
      status: 400,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message || 'Sunucu hatası' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})
