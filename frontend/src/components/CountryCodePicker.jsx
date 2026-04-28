import { useState, useRef, useEffect } from 'react';

const COUNTRIES = [
  // ── Most common at top ──────────────────────────────────
  { code: '+91',  flag: '🇮🇳', name: 'India'          },
  { code: '+1',   flag: '🇺🇸', name: 'USA / Canada'   },
  { code: '+44',  flag: '🇬🇧', name: 'United Kingdom' },
  { code: '+61',  flag: '🇦🇺', name: 'Australia'      },
  { code: '+971', flag: '🇦🇪', name: 'UAE'            },
  { code: '+65',  flag: '🇸🇬', name: 'Singapore'      },
  { code: '+60',  flag: '🇲🇾', name: 'Malaysia'       },
  { code: '+966', flag: '🇸🇦', name: 'Saudi Arabia'   },
  { code: '+974', flag: '🇶🇦', name: 'Qatar'          },
  // ── Rest (alphabetical) ──────────────────────────────────
  { code: '+93',  flag: '🇦🇫', name: 'Afghanistan'    },
  { code: '+355', flag: '🇦🇱', name: 'Albania'        },
  { code: '+213', flag: '🇩🇿', name: 'Algeria'        },
  { code: '+376', flag: '🇦🇩', name: 'Andorra'        },
  { code: '+244', flag: '🇦🇴', name: 'Angola'         },
  { code: '+54',  flag: '🇦🇷', name: 'Argentina'      },
  { code: '+374', flag: '🇦🇲', name: 'Armenia'        },
  { code: '+43',  flag: '🇦🇹', name: 'Austria'        },
  { code: '+994', flag: '🇦🇿', name: 'Azerbaijan'     },
  { code: '+880', flag: '🇧🇩', name: 'Bangladesh'     },
  { code: '+32',  flag: '🇧🇪', name: 'Belgium'        },
  { code: '+591', flag: '🇧🇴', name: 'Bolivia'        },
  { code: '+387', flag: '🇧🇦', name: 'Bosnia'         },
  { code: '+55',  flag: '🇧🇷', name: 'Brazil'         },
  { code: '+359', flag: '🇧🇬', name: 'Bulgaria'       },
  { code: '+855', flag: '🇰🇭', name: 'Cambodia'       },
  { code: '+237', flag: '🇨🇲', name: 'Cameroon'       },
  { code: '+56',  flag: '🇨🇱', name: 'Chile'          },
  { code: '+86',  flag: '🇨🇳', name: 'China'          },
  { code: '+57',  flag: '🇨🇴', name: 'Colombia'       },
  { code: '+243', flag: '🇨🇩', name: 'Congo (DRC)'    },
  { code: '+506', flag: '🇨🇷', name: 'Costa Rica'     },
  { code: '+385', flag: '🇭🇷', name: 'Croatia'        },
  { code: '+53',  flag: '🇨🇺', name: 'Cuba'           },
  { code: '+357', flag: '🇨🇾', name: 'Cyprus'         },
  { code: '+420', flag: '🇨🇿', name: 'Czech Republic' },
  { code: '+45',  flag: '🇩🇰', name: 'Denmark'        },
  { code: '+20',  flag: '🇪🇬', name: 'Egypt'          },
  { code: '+372', flag: '🇪🇪', name: 'Estonia'        },
  { code: '+251', flag: '🇪🇹', name: 'Ethiopia'       },
  { code: '+358', flag: '🇫🇮', name: 'Finland'        },
  { code: '+33',  flag: '🇫🇷', name: 'France'         },
  { code: '+995', flag: '🇬🇪', name: 'Georgia'        },
  { code: '+49',  flag: '🇩🇪', name: 'Germany'        },
  { code: '+233', flag: '🇬🇭', name: 'Ghana'          },
  { code: '+30',  flag: '🇬🇷', name: 'Greece'         },
  { code: '+502', flag: '🇬🇹', name: 'Guatemala'      },
  { code: '+224', flag: '🇬🇳', name: 'Guinea'         },
  { code: '+504', flag: '🇭🇳', name: 'Honduras'       },
  { code: '+36',  flag: '🇭🇺', name: 'Hungary'        },
  { code: '+354', flag: '🇮🇸', name: 'Iceland'        },
  { code: '+62',  flag: '🇮🇩', name: 'Indonesia'      },
  { code: '+98',  flag: '🇮🇷', name: 'Iran'           },
  { code: '+964', flag: '🇮🇶', name: 'Iraq'           },
  { code: '+353', flag: '🇮🇪', name: 'Ireland'        },
  { code: '+972', flag: '🇮🇱', name: 'Israel'         },
  { code: '+39',  flag: '🇮🇹', name: 'Italy'          },
  { code: '+81',  flag: '🇯🇵', name: 'Japan'          },
  { code: '+962', flag: '🇯🇴', name: 'Jordan'         },
  { code: '+7',   flag: '🇰🇿', name: 'Kazakhstan'     },
  { code: '+254', flag: '🇰🇪', name: 'Kenya'          },
  { code: '+82',  flag: '🇰🇷', name: 'South Korea'    },
  { code: '+965', flag: '🇰🇼', name: 'Kuwait'         },
  { code: '+996', flag: '🇰🇬', name: 'Kyrgyzstan'     },
  { code: '+856', flag: '🇱🇦', name: 'Laos'           },
  { code: '+371', flag: '🇱🇻', name: 'Latvia'         },
  { code: '+961', flag: '🇱🇧', name: 'Lebanon'        },
  { code: '+231', flag: '🇱🇷', name: 'Liberia'        },
  { code: '+218', flag: '🇱🇾', name: 'Libya'          },
  { code: '+370', flag: '🇱🇹', name: 'Lithuania'      },
  { code: '+352', flag: '🇱🇺', name: 'Luxembourg'     },
  { code: '+261', flag: '🇲🇬', name: 'Madagascar'     },
  { code: '+265', flag: '🇲🇼', name: 'Malawi'         },
  { code: '+960', flag: '🇲🇻', name: 'Maldives'       },
  { code: '+223', flag: '🇲🇱', name: 'Mali'           },
  { code: '+356', flag: '🇲🇹', name: 'Malta'          },
  { code: '+52',  flag: '🇲🇽', name: 'Mexico'         },
  { code: '+373', flag: '🇲🇩', name: 'Moldova'        },
  { code: '+212', flag: '🇲🇦', name: 'Morocco'        },
  { code: '+258', flag: '🇲🇿', name: 'Mozambique'     },
  { code: '+95',  flag: '🇲🇲', name: 'Myanmar'        },
  { code: '+264', flag: '🇳🇦', name: 'Namibia'        },
  { code: '+977', flag: '🇳🇵', name: 'Nepal'          },
  { code: '+31',  flag: '🇳🇱', name: 'Netherlands'    },
  { code: '+64',  flag: '🇳🇿', name: 'New Zealand'    },
  { code: '+505', flag: '🇳🇮', name: 'Nicaragua'      },
  { code: '+234', flag: '🇳🇬', name: 'Nigeria'        },
  { code: '+47',  flag: '🇳🇴', name: 'Norway'         },
  { code: '+968', flag: '🇴🇲', name: 'Oman'           },
  { code: '+92',  flag: '🇵🇰', name: 'Pakistan'       },
  { code: '+507', flag: '🇵🇦', name: 'Panama'         },
  { code: '+675', flag: '🇵🇬', name: 'Papua New Guinea'},
  { code: '+595', flag: '🇵🇾', name: 'Paraguay'       },
  { code: '+51',  flag: '🇵🇪', name: 'Peru'           },
  { code: '+63',  flag: '🇵🇭', name: 'Philippines'    },
  { code: '+48',  flag: '🇵🇱', name: 'Poland'         },
  { code: '+351', flag: '🇵🇹', name: 'Portugal'       },
  { code: '+1787',flag: '🇵🇷', name: 'Puerto Rico'    },
  { code: '+40',  flag: '🇷🇴', name: 'Romania'        },
  { code: '+7',   flag: '🇷🇺', name: 'Russia'         },
  { code: '+250', flag: '🇷🇼', name: 'Rwanda'         },
  { code: '+966', flag: '🇸🇦', name: 'Saudi Arabia'   },
  { code: '+221', flag: '🇸🇳', name: 'Senegal'        },
  { code: '+381', flag: '🇷🇸', name: 'Serbia'         },
  { code: '+232', flag: '🇸🇱', name: 'Sierra Leone'   },
  { code: '+421', flag: '🇸🇰', name: 'Slovakia'       },
  { code: '+386', flag: '🇸🇮', name: 'Slovenia'       },
  { code: '+252', flag: '🇸🇴', name: 'Somalia'        },
  { code: '+27',  flag: '🇿🇦', name: 'South Africa'   },
  { code: '+34',  flag: '🇪🇸', name: 'Spain'          },
  { code: '+94',  flag: '🇱🇰', name: 'Sri Lanka'      },
  { code: '+249', flag: '🇸🇩', name: 'Sudan'          },
  { code: '+46',  flag: '🇸🇪', name: 'Sweden'         },
  { code: '+41',  flag: '🇨🇭', name: 'Switzerland'    },
  { code: '+963', flag: '🇸🇾', name: 'Syria'          },
  { code: '+886', flag: '🇹🇼', name: 'Taiwan'         },
  { code: '+255', flag: '🇹🇿', name: 'Tanzania'       },
  { code: '+66',  flag: '🇹🇭', name: 'Thailand'       },
  { code: '+216', flag: '🇹🇳', name: 'Tunisia'        },
  { code: '+90',  flag: '🇹🇷', name: 'Turkey'         },
  { code: '+256', flag: '🇺🇬', name: 'Uganda'         },
  { code: '+380', flag: '🇺🇦', name: 'Ukraine'        },
  { code: '+598', flag: '🇺🇾', name: 'Uruguay'        },
  { code: '+998', flag: '🇺🇿', name: 'Uzbekistan'     },
  { code: '+58',  flag: '🇻🇪', name: 'Venezuela'      },
  { code: '+84',  flag: '🇻🇳', name: 'Vietnam'        },
  { code: '+967', flag: '🇾🇪', name: 'Yemen'          },
  { code: '+260', flag: '🇿🇲', name: 'Zambia'         },
  { code: '+263', flag: '🇿🇼', name: 'Zimbabwe'       },
];

/**
 * A compact country-code selector.
 *
 * Props:
 *   value    – current code string, e.g. "+91"
 *   onChange – (code: string) => void
 *   style    – optional extra styles for the trigger button
 */
export default function CountryCodePicker({ value = '+91', onChange, style }) {
  const [open,   setOpen]   = useState(false);
  const [search, setSearch] = useState('');
  const ref   = useRef(null);

  const selected = COUNTRIES.find(c => c.code === value) ?? COUNTRIES[0];

  const filtered = COUNTRIES.filter(c =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.code.includes(search)
  );

  // Close on outside click
  useEffect(() => {
    const handler = e => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false);
        setSearch('');
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const select = country => {
    onChange(country.code);
    setOpen(false);
    setSearch('');
  };

  return (
    <div ref={ref} className="relative flex-shrink-0" style={style}>
      {/* Trigger button */}
      <button
        type="button"
        onClick={() => setOpen(v => !v)}
        className="flex items-center gap-1.5 h-full px-3 rounded-l-xl transition-all"
        style={{
          background: 'rgba(255,255,255,0.10)',
          border: '1px solid rgba(255,255,255,0.22)',
          borderRight: 'none',
          color: 'white',
          fontSize: '0.875rem',
          fontWeight: 600,
          whiteSpace: 'nowrap',
          minWidth: '72px',
        }}>
        <span className="text-lg leading-none">{selected.flag}</span>
        <span>{selected.code}</span>
        <span className="text-white/40 text-xs" style={{ marginLeft: 2 }}>▾</span>
      </button>

      {/* Dropdown */}
      {open && (
        <div
          className="absolute left-0 top-full mt-1 rounded-xl overflow-hidden shadow-2xl z-50"
          style={{
            background: 'rgba(15,23,42,0.97)',
            backdropFilter: 'blur(16px)',
            border: '1px solid rgba(255,255,255,0.12)',
            width: '240px',
            maxHeight: '300px',
            display: 'flex',
            flexDirection: 'column',
          }}>

          {/* Search */}
          <div className="px-3 py-2 border-b" style={{ borderColor: 'rgba(255,255,255,0.1)' }}>
            <input
              autoFocus
              type="text"
              placeholder="Search country…"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full text-sm text-white outline-none"
              style={{
                background: 'rgba(255,255,255,0.08)',
                border: '1px solid rgba(255,255,255,0.15)',
                borderRadius: '8px',
                padding: '6px 10px',
                caretColor: 'white',
              }}
            />
          </div>

          {/* List */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {filtered.length === 0 ? (
              <p className="text-white/40 text-xs text-center py-4">No results</p>
            ) : filtered.map((c, i) => (
              <button
                key={`${c.code}-${i}`}
                type="button"
                onClick={() => select(c)}
                className="w-full flex items-center gap-2.5 px-3 py-2 text-left transition-colors"
                style={{
                  background: c.code === value ? 'rgba(14,165,233,0.2)' : 'transparent',
                  color: c.code === value ? '#7dd3fc' : 'rgba(255,255,255,0.85)',
                  fontSize: '0.8rem',
                }}
                onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.08)'}
                onMouseLeave={e => e.currentTarget.style.background =
                  c.code === value ? 'rgba(14,165,233,0.2)' : 'transparent'}>
                <span className="text-base leading-none">{c.flag}</span>
                <span className="flex-1 truncate">{c.name}</span>
                <span className="flex-shrink-0 font-mono font-semibold text-white/50">{c.code}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
