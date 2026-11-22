import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { homePath } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, radius, textStyles } from '../../theme/tokens';

const pillSx = {
  ...textStyles.body,
  display: 'inline-flex',
  alignItems: 'center',
  gap: '4px',
  px: '14px',
  py: '9px',
  borderRadius: `${radius.full}px`,
  border: `1px solid ${c('border-strong')}`,
  color: c('text-primary'),
  whiteSpace: 'nowrap',
  flexShrink: 0,
} as const;

// Переключатель ролей (ui-requirements.md §3.3) — «таблетка» с ролью в шапке. Выбранная роль уходит
// в X-Active-Role, кэш сбрасывается, открывается главный экран роли.
export function RoleSwitcher() {
  const { roles, activeRole, setActiveRole } = useSession();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  if (roles.length === 0 || activeRole === null) return null;
  if (roles.length === 1) {
    return (
      <Box sx={pillSx} data-testid="active-role">
        {texts.roles[activeRole]}
      </Box>
    );
  }

  return (
    <>
      <ButtonBase
        sx={{ ...pillSx, pr: '10px', '&:hover': { bgcolor: c('bg-surface-2') } }}
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-label={`${texts.session.roleSwitcher}: ${texts.roles[activeRole]}`}
        aria-haspopup="menu"
        aria-expanded={anchor ? 'true' : undefined}
        data-testid="role-switcher"
      >
        {texts.roles[activeRole]}
        <ExpandMoreIcon sx={{ fontSize: 18, color: c('text-muted') }} />
      </ButtonBase>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        {roles.map((role) => (
          <MenuItem
            key={role}
            selected={role === activeRole}
            onClick={() => {
              setAnchor(null);
              if (role === activeRole) return;
              setActiveRole(role);
              void navigate(homePath(role));
            }}
          >
            {texts.roles[role]}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
