$content = Get-Content 'c:\B\2d3d\2d3d\specs\sprints\sprint-1.md' -Raw -Encoding UTF8

function Get-AcBlock {
    param([string]$fileContent, [string]$id)
    $pattern = '(?s)<ac-block id="' + [regex]::Escape($id) + '">(.*?)</ac-block>'
    $m = [regex]::Match($fileContent, $pattern)
    if ($m.Success) {
        return $m.Groups[1].Value
    }
    return $null
}

$ids = @(
    'S1-PR1-AC1',
    'S1-PR1.5-AC1',
    'S1-PR2-AC1',
    'S1-PR3-AC1',
    'S1-PR4-AC1',
    'S1-PR5-AC1',
    'S1-PR6-AC1',
    'S1-PR7-AC1',
    'S1-PR8-AC1',
    'S1-PR9-AC1',
    'S1-PR10-AC1',
    'S1-PR11-AC1',
    'S1-PR12-AC1',
    'S1-PR13-AC1',
    'S1-PR14-AC1',
    'S1-PR15-AC1',
    'S1-PR16-AC1',
    'S1-PR17-AC1',
    'S1-PR18-AC1'
)

foreach ($id in $ids) {
    $block = Get-AcBlock -fileContent $content -id $id
    if ($null -ne $block) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($block)
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash($bytes)
        $hashHex = [BitConverter]::ToString($hashBytes) -replace '-',''
        Write-Output "$id`t$($hashHex.ToLower())"
    } else {
        Write-Output "$id`tNOT FOUND"
    }
}
