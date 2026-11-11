import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router';
import type { Role } from '../../api/types';
import { homePath } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';

// Переключатель ролей (ui-requirements.md §3.3): выбранная роль уходит в X-Active-Role,
// кэш сбрасывается, открывается главный экран роли.
export function RoleSwitcher() {
  const { roles, activeRole, setActiveRole } = useSession();
  const navigate = useNavigate();

  if (roles.length === 0 || activeRole === null) return null;
  if (roles.length === 1) {
    return (
      <Typography variant="body2" sx={{ opacity: 0.9 }} data-testid="active-role">
        {texts.roles[activeRole]}
      </Typography>
    );
  }

  return (
    <FormControl size="small" sx={{ minWidth: 220 }}>
      <Select
        value={activeRole}
        onChange={(event) => {
          const role = event.target.value as Role;
          setActiveRole(role);
          void navigate(homePath(role));
        }}
        inputProps={{ 'aria-label': texts.session.roleSwitcher, 'data-testid': 'role-switcher' }}
        sx={{ bgcolor: 'rgba(255,255,255,0.12)', color: 'inherit', '& .MuiSvgIcon-root': { color: 'inherit' } }}
      >
        {roles.map((role) => (
          <MenuItem key={role} value={role}>
            {texts.roles[role]}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}
