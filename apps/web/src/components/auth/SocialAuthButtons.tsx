import { motion } from 'framer-motion';

type Provider = 'google' | 'github' | 'linkedin';

interface ProviderConfig {
  label: string;
  icon: React.ReactNode;
  hoverClass: string;
}

const PROVIDERS: Record<Provider, ProviderConfig> = {
  google: {
    label: 'Google',
    hoverClass: 'hover:border-[#4285F4]/50 hover:bg-[#4285F4]/8',
    icon: (
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
        <path
          d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z"
          fill="#4285F4"
        />
        <path
          d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18Z"
          fill="#34A853"
        />
        <path
          d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z"
          fill="#FBBC05"
        />
        <path
          d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z"
          fill="#EA4335"
        />
      </svg>
    ),
  },
  github: {
    label: 'GitHub',
    hoverClass: 'hover:border-[#f0f6fc]/30 hover:bg-white/8',
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className="text-[#f0f6fc]">
        <path d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12Z" />
      </svg>
    ),
  },
  linkedin: {
    label: 'LinkedIn',
    hoverClass: 'hover:border-[#0A66C2]/50 hover:bg-[#0A66C2]/10',
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="#0A66C2" aria-hidden="true">
        <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286ZM5.337 7.433a2.062 2.062 0 0 1-2.063-2.065 2.064 2.064 0 1 1 2.063 2.065Zm1.782 13.019H3.555V9h3.564v11.452ZM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003Z" />
      </svg>
    ),
  },
};

const OAUTH2_BASE = '/oauth2/authorization';

interface SocialAuthButtonsProps {
  mode: 'signin' | 'signup';
}

export default function SocialAuthButtons({ mode }: SocialAuthButtonsProps) {
  const handleOAuth = (provider: Provider) => {
    window.location.href = `${OAUTH2_BASE}/${provider}`;
  };

  const verb = mode === 'signin' ? 'Continue' : 'Sign up';

  return (
    <div className="space-y-2.5">
      {(Object.entries(PROVIDERS) as [Provider, ProviderConfig][]).map(
        ([provider, config], i) => (
          <motion.button
            key={provider}
            type="button"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            whileTap={{ scale: 0.98 }}
            onClick={() => handleOAuth(provider)}
            className={`
              w-full flex items-center gap-3 px-4 py-2.5 rounded-lg
              bg-bg-elevated border border-border-default
              text-text-secondary text-sm font-medium
              transition-all duration-150 cursor-pointer
              ${config.hoverClass}
              hover:text-text-primary
            `}
          >
            <span className="shrink-0 w-[18px] flex items-center justify-center">
              {config.icon}
            </span>
            <span className="flex-1 text-center">
              {verb} with {config.label}
            </span>
          </motion.button>
        )
      )}
    </div>
  );
}
