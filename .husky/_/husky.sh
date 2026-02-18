#!/usr/bin/env sh
if [ -z "$husky_skip_init" ]; then
  debug () {
    [ "$HUSKY" = "2" ] && echo "husky (debug) - $1"
  }
  readonly hook_name="$(basename -- "$0")"
  debug "starting $hook_name..."
  if [ "$HUSKY" = "0" ]; then
    debug "HUSKY env variable is set to 0, skipping hook"
    exit 0
  fi
  if [ -f ~/.huskyrc ]; then
    debug "sourcing ~/.huskyrc"
    . ~/.huskyrc
  fi
  readonly husky_skip_init=1
  export husky_skip_init
  sh -e "$0" "$@"
  exit_code="$?"
  if [ $exit_code != 0 ]; then
    echo "husky - $hook_name 스크립트 실패 (코드 $exit_code)"
  fi
  exit $exit_code
fi
